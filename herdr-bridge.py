#!/usr/bin/env python3
"""
Herdr Remote WebSocket Bridge & Execution Engine (Enhanced)
Bridges ~/.config/herdr/herdr.sock and Herdr CLI to a WebSocket server on port 8765.
Supports multi-tab sync, reading all terminal pane buffers, and bi-directional prompt execution.
"""

import asyncio
import json
import os
import socket
import subprocess
import sys
import websockets

HERDR_SOCK_PATH = os.path.expanduser("~/.config/herdr/herdr.sock")
WS_PORT = 8765

def query_herdr_socket(method, params=None):
    if params is None:
        params = {}
    if not os.path.exists(HERDR_SOCK_PATH):
        return {"error": "herdr.sock not found. Is herdr server running?"}
    try:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.settimeout(2.0)
        sock.connect(HERDR_SOCK_PATH)
        req = {"id": "bridge_req", "method": method, "params": params}
        sock.sendall((json.dumps(req) + "\n").encode("utf-8"))
        data = sock.recv(131072)
        sock.close()
        return json.loads(data.decode("utf-8"))
    except Exception as e:
        return {"error": str(e)}

def read_pane_terminal(pane_id):
    """
    Reads terminal output from a pane using 'herdr pane read' (works for all shells and agent panes).
    """
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "pane", "read", pane_id],
            capture_output=True,
            text=True,
            timeout=3.0
        )
        if res.returncode == 0 and res.stdout:
            return res.stdout.strip()
    except Exception as e:
        print(f"[HerdrBridge] Error reading pane {pane_id}: {e}")
    return ""

def send_prompt_to_pane(pane_id, prompt_text):
    """
    Sends input to a pane. First tries 'herdr agent prompt', then falls back to 'herdr pane send-text'.
    """
    # 1. Try agent prompt
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "agent", "prompt", pane_id, prompt_text],
            capture_output=True,
            text=True,
            timeout=4.0
        )
        if res.returncode == 0:
            return True
    except Exception:
        pass
    
    # 2. Fallback to pane send-text
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "pane", "send-text", pane_id, prompt_text + "\n"],
            capture_output=True,
            text=True,
            timeout=4.0
        )
        return res.returncode == 0
    except Exception as e:
        print(f"[HerdrBridge] Error sending text to pane {pane_id}: {e}")
        return False

def get_synced_sessions_payload():
    resp = query_herdr_socket("session.snapshot")
    snapshot = resp.get("result", {}).get("snapshot", {})
    tabs = snapshot.get("tabs", [])
    panes = snapshot.get("panes", [])
    agents = snapshot.get("agents", [])
    
    sessions_list = []
    
    for tab in tabs:
        tab_id = tab.get("tab_id", "")
        label = tab.get("label", "")
        pane = next((p for p in panes if p.get("tab_id") == tab_id), {})
        pane_id = pane.get("pane_id", tab_id)
        
        agent_obj = next((a for a in agents if a.get("tab_id") == tab_id), {})
        agent_name = agent_obj.get("agent") or pane.get("agent") or pane.get("terminal_title") or "Terminal"
        cwd = pane.get("cwd", "")
        project_name = os.path.basename(cwd) if cwd else ""
        
        title = f"{agent_name.upper()} • {project_name}" if project_name else f"{agent_name.upper()} (Tab {label})"
        role = f"Herdr Workspace Agent ({project_name or cwd})" if cwd else "Herdr Agent"
        status = "ONLINE" if tab.get("agent_status") in ("idle", "running", "thinking") else "ONLINE"
        
        sessions_list.append({
            "id": tab_id,
            "session_id": tab_id,
            "pane_id": pane_id,
            "title": title,
            "name": title,
            "role": role,
            "status": status,
            "agent": agent_name,
            "cwd": cwd,
            "model": "herdr/desktop"
        })
        
    return {
        "type": "sessions_list",
        "sessions": sessions_list,
        "active_sessions": sessions_list,
        "count": len(sessions_list)
    }

async def stream_tab_history(websocket, tab_id, pane_id):
    output = read_pane_terminal(pane_id)
    if output:
        clean_output = "\n".join(output.splitlines()[-45:])
        payload = {
            "type": "message_complete",
            "session_id": tab_id,
            "id": f"msg_init_{tab_id}",
            "content": f"```terminal\n{clean_output}\n```"
        }
        await websocket.send(json.dumps(payload))

async def handle_client(websocket):
    client_ip = websocket.remote_address
    print(f"[HerdrBridge] Client connected from {client_ip}")
    
    # 1. Send live sessions
    initial_payload = get_synced_sessions_payload()
    sessions = initial_payload.get("sessions", [])
    print(f"[HerdrBridge] Dispatched {len(sessions)} desktop tabs to client")
    await websocket.send(json.dumps(initial_payload))
    
    # 2. For EVERY tab, read pane output via 'herdr pane read' and send content
    for session in sessions:
        tab_id = session.get("id")
        pane_id = session.get("pane_id") or tab_id
        await stream_tab_history(websocket, tab_id, pane_id)
        await asyncio.sleep(0.05)
    
    try:
        async for message in websocket:
            print(f"[HerdrBridge] Incoming: {message}")
            try:
                data = json.loads(message)
                msg_type = data.get("type", "")
                
                if msg_type in ("get_sessions", "list_sessions", "sync_tabs", "get_tabs", "client_hello"):
                    payload = get_synced_sessions_payload()
                    await websocket.send(json.dumps(payload))
                    for session in payload.get("sessions", []):
                        tab_id = session.get("id")
                        pane_id = session.get("pane_id") or tab_id
                        await stream_tab_history(websocket, tab_id, pane_id)
                elif msg_type in ("get_tab_content", "select_tab", "focus_tab"):
                    target_id = data.get("session_id") or data.get("tab_id") or ""
                    # Find pane
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == target_id), {})
                    pane_id = session.get("pane_id") or target_id
                    if pane_id:
                        await stream_tab_history(websocket, target_id, pane_id)
                elif msg_type == "user_message":
                    session_id = data.get("session_id", "")
                    content = data.get("content", "")
                    
                    # Find target pane
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == session_id), {})
                    pane_id = session.get("pane_id") or session_id
                    
                    print(f"[HerdrBridge] Routing user prompt to pane {pane_id} (tab {session_id}): {content}")
                    
                    await websocket.send(json.dumps({
                        "type": "agent_status",
                        "session_id": session_id,
                        "status": "THINKING",
                        "detail": "Executing prompt in Herdr terminal..."
                    }))
                    
                    # Submit to desktop herdr pane/agent
                    send_prompt_to_pane(pane_id, content)
                    
                    await asyncio.sleep(1.2)
                    
                    # Read updated terminal output
                    updated_output = read_pane_terminal(pane_id)
                    tail_output = "\n".join(updated_output.splitlines()[-30:]) if updated_output else "Dispatched to terminal."
                    
                    await websocket.send(json.dumps({
                        "type": "agent_status",
                        "session_id": session_id,
                        "status": "ONLINE",
                        "detail": "Online • Ready"
                    }))
                    
                    await websocket.send(json.dumps({
                        "type": "message_complete",
                        "session_id": session_id,
                        "content": f"⚡ **Prompt dispatched to Herdr Agent**:\n\n```terminal\n{tail_output}\n```"
                    }))
            except Exception as e:
                print(f"[HerdrBridge] Error processing message: {e}")
    except websockets.exceptions.ConnectionClosed:
        print(f"[HerdrBridge] Client disconnected: {client_ip}")

async def main():
    print(f"🚀 Starting Herdr WebSocket Bridge & Terminal Daemon on 0.0.0.0:{WS_PORT}...")
    async with websockets.serve(handle_client, "0.0.0.0", WS_PORT):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
