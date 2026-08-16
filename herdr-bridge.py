#!/usr/bin/env python3
"""
Herdr Remote WebSocket Bridge & Real-Time Streaming Daemon
Bridges ~/.config/herdr/herdr.sock to a WebSocket server on port 8765.
Features:
- Submits prompts to agent if agent is running
- If NO agent is running, submits command directly to bash / zsh shell with Enter execution
- Streams clean agent output and terminal output directly to Android
- Preserves markdown formatting so code blocks are rendered as cards and text as Compose typography
- Real-time tab sync when new tabs open or close on desktop
"""

import asyncio
import hashlib
import json
import os
import re
import socket
import subprocess
import sys
import time
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

def clean_terminal_buffer(raw_text):
    if not raw_text:
        return ""
    
    # Strip ANSI escape sequences
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    text = ansi_escape.sub('', raw_text)
    
    lines = text.splitlines()
    cleaned = []
    prev_was_hr = False
    
    for line in lines:
        stripped = line.strip()
        is_hr = bool(stripped) and all(c in '─━═-_~' for c in stripped) and len(stripped) >= 3
        if is_hr:
            if not prev_was_hr:
                cleaned.append("────────────────────────────────")
                prev_was_hr = True
        else:
            prev_was_hr = False
            cleaned.append(line)
            
    return "\n".join(cleaned)

def read_pane_terminal(pane_id):
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "pane", "read", pane_id],
            capture_output=True,
            text=True,
            timeout=2.0
        )
        if res.returncode == 0 and res.stdout:
            cleaned = clean_terminal_buffer(res.stdout.strip())
            return cleaned
    except Exception:
        pass
    return ""

def submit_prompt_to_pane(pane_id, prompt_text):
    print(f"[HerdrBridge] Dispatching prompt/command to pane {pane_id}: {prompt_text}")
    
    # 1. Check if pane has an active registered agent
    resp = query_herdr_socket("session.snapshot")
    agents = resp.get("result", {}).get("snapshot", {}).get("agents", [])
    has_agent = any(a.get("pane_id") == pane_id or a.get("tab_id") == pane_id for a in agents)
    
    if has_agent:
        try:
            res = subprocess.run(
                ["/Users/chetan/.local/bin/herdr", "agent", "prompt", pane_id, prompt_text],
                capture_output=True,
                text=True,
                timeout=3.0
            )
            if res.returncode == 0:
                print(f"[HerdrBridge] Agent prompt succeeded on {pane_id}")
                return True
        except Exception as e:
            print(f"[HerdrBridge] Agent prompt error: {e}")

    # 2. No agent running -> run directly in bash/zsh shell
    print(f"[HerdrBridge] Submitting directly to bash/zsh shell on {pane_id}")
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "pane", "run", pane_id, prompt_text],
            capture_output=True,
            text=True,
            timeout=3.0
        )
        if res.returncode == 0:
            print(f"[HerdrBridge] Pane run succeeded on {pane_id}")
            return True
    except Exception:
        pass

    # 3. Fallback: send-text + send-keys Enter
    try:
        subprocess.run(["/Users/chetan/.local/bin/herdr", "pane", "send-text", pane_id, prompt_text], capture_output=True, text=True, timeout=2.0)
        time.sleep(0.05)
        subprocess.run(["/Users/chetan/.local/bin/herdr", "pane", "send-keys", pane_id, "Enter"], capture_output=True, text=True, timeout=2.0)
        print(f"[HerdrBridge] Pane send-text + Enter executed on {pane_id}")
        return True
    except Exception as e:
        print(f"[HerdrBridge] Error submitting command: {e}")
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
        
        agent_obj = next((a for a in agents if a.get("tab_id") == tab_id or a.get("pane_id") == pane_id), {})
        is_active_agent = bool(agent_obj.get("agent"))
        agent_name = agent_obj.get("agent") or pane.get("agent") or pane.get("terminal_title") or "Terminal"
        cwd = pane.get("cwd", "")
        project_name = os.path.basename(cwd) if cwd else ""
        
        title = f"{agent_name.upper()} • {project_name}" if project_name else f"{agent_name.upper()} (Tab {label})"
        role = f"Herdr Agent in {project_name or cwd}" if is_active_agent else f"Bash Shell in {project_name or cwd}"
        status = "ONLINE"
        
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
            "is_agent": is_active_agent,
            "model": "herdr/desktop"
        })
        
    return {
        "type": "sessions_list",
        "sessions": sessions_list,
        "active_sessions": sessions_list,
        "count": len(sessions_list)
    }

async def live_terminal_watcher(websocket, client_ip):
    last_tab_hash = ""
    last_pane_hashes = {}
    
    try:
        while True:
            await asyncio.sleep(0.4)
            
            # 1. Check if tabs changed
            payload = get_synced_sessions_payload()
            sessions = payload.get("sessions", [])
            tab_ids = ",".join(s["id"] for s in sessions)
            
            if tab_ids != last_tab_hash:
                last_tab_hash = tab_ids
                await websocket.send(json.dumps(payload))
            
            # 2. Check each pane for output changes
            for session in sessions:
                tab_id = session.get("id")
                pane_id = session.get("pane_id") or tab_id
                
                output = read_pane_terminal(pane_id)
                if not output:
                    continue
                
                out_hash = hashlib.md5(output.encode("utf-8")).hexdigest()
                if last_pane_hashes.get(tab_id) != out_hash:
                    last_pane_hashes[tab_id] = out_hash
                    
                    clean_output = "\n".join(output.splitlines()[-45:])
                    msg_payload = {
                        "type": "message_complete",
                        "session_id": tab_id,
                        "id": f"msg_terminal_{tab_id}",
                        "content": clean_output
                    }
                    await websocket.send(json.dumps(msg_payload))
    except (asyncio.CancelledError, websockets.exceptions.ConnectionClosed):
        pass

async def handle_client(websocket):
    client_ip = websocket.remote_address
    print(f"[HerdrBridge] Client connected from {client_ip}")
    
    # 1. Send live sessions
    initial_payload = get_synced_sessions_payload()
    sessions = initial_payload.get("sessions", [])
    print(f"[HerdrBridge] Dispatched {len(sessions)} desktop tabs to client")
    await websocket.send(json.dumps(initial_payload))
    
    # 2. Initial stream for all tabs
    for session in sessions:
        tab_id = session.get("id")
        pane_id = session.get("pane_id") or tab_id
        output = read_pane_terminal(pane_id)
        if output:
            clean_output = "\n".join(output.splitlines()[-45:])
            await websocket.send(json.dumps({
                "type": "message_complete",
                "session_id": tab_id,
                "id": f"msg_terminal_{tab_id}",
                "content": clean_output
            }))
            await asyncio.sleep(0.02)
    
    # 3. Start real-time background watcher task
    watcher_task = asyncio.create_task(live_terminal_watcher(websocket, client_ip))
    
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                msg_type = data.get("type", "")
                
                if msg_type in ("get_sessions", "list_sessions", "sync_tabs", "get_tabs", "client_hello"):
                    payload = get_synced_sessions_payload()
                    await websocket.send(json.dumps(payload))
                elif msg_type in ("get_tab_content", "select_tab", "focus_tab"):
                    target_id = data.get("session_id") or data.get("tab_id") or ""
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == target_id), {})
                    pane_id = session.get("pane_id") or target_id
                    if pane_id:
                        output = read_pane_terminal(pane_id)
                        if output:
                            clean_output = "\n".join(output.splitlines()[-45:])
                            await websocket.send(json.dumps({
                                "type": "message_complete",
                                "session_id": target_id,
                                "id": f"msg_terminal_{target_id}",
                                "content": clean_output
                            }))
                elif msg_type == "user_message":
                    session_id = data.get("session_id", "")
                    content = data.get("content", "")
                    
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == session_id), {})
                    pane_id = session.get("pane_id") or session_id
                    
                    # Submit command to agent/bash
                    submit_prompt_to_pane(pane_id, content)
                    await asyncio.sleep(0.4)
                    
                    updated_output = read_pane_terminal(pane_id)
                    tail_output = "\n".join(updated_output.splitlines()[-45:]) if updated_output else "Command executed."
                    
                    await websocket.send(json.dumps({
                        "type": "message_complete",
                        "session_id": session_id,
                        "id": f"msg_terminal_{session_id}",
                        "content": tail_output
                    }))
            except Exception as e:
                print(f"[HerdrBridge] Error processing message: {e}")
    except websockets.exceptions.ConnectionClosed:
        print(f"[HerdrBridge] Client disconnected: {client_ip}")
    finally:
        watcher_task.cancel()

async def main():
    print(f"🚀 Starting Real-Time Herdr WebSocket Bridge & Watcher Daemon on 0.0.0.0:{WS_PORT}...")
    async with websockets.serve(handle_client, "0.0.0.0", WS_PORT):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
