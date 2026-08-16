#!/usr/bin/env python3
"""
Herdr Remote WebSocket Bridge & Real-Time Bi-Directional Workspace Sync Daemon
Bridges ~/.config/herdr/herdr.sock to WebSocket on port 8765.

Full-Fidelity Unwrapped Turn Streaming & Universal Tab Refresh:
- Tab Refresh pulls latest content, agent outputs, and scrollback for ALL tabs simultaneously.
- Uses unwrapped scrollback reader (`herdr pane read <pane> --lines 800 --source recent-unwrapped`).
- Exact prompt boundary extraction prevents trimming.
- Live real-time streaming to the active agent bubble.
- Full bi-directional tab synchronization & exact 1-to-1 tab naming.
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

pane_turn_prompts = {}
pane_turn_baselines = {}
pane_last_streamed = {}

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
    
    lines = []
    prev_was_divider = False
    
    for line in text.splitlines():
        l = line.strip()
        # Filter out interactive terminal chrome and footers
        if "esc to cancel" in l or "Tip: " in l or "Loading..." in l or "Running..." in l:
            continue
        if l.startswith("● [") and "running" in l:
            continue
        
        # Collapse and convert horizontal rule lines into standard Markdown separator ---
        is_divider = bool(l) and (all(c in '─━═-_~=─ ' for c in l) and len(l) >= 3)
        if is_divider:
            if not prev_was_divider:
                lines.append("\n---\n")
                prev_was_divider = True
        else:
            prev_was_divider = False
            lines.append(line)
        
    combined = "\n".join(lines)
    combined = re.sub(r'\n{3,}', '\n\n', combined).strip()
    return combined

def read_pane_terminal(pane_id, lines=800):
    try:
        res = subprocess.run(
            ["/Users/chetan/.local/bin/herdr", "pane", "read", pane_id, "--lines", str(lines), "--source", "recent-unwrapped"],
            capture_output=True,
            text=True,
            timeout=2.5
        )
        if res.returncode == 0 and res.stdout:
            cleaned = clean_terminal_buffer(res.stdout)
            return cleaned
    except Exception:
        pass
    return ""

def get_pane_turn_output(pane_id):
    full_output = read_pane_terminal(pane_id, lines=800)
    if not full_output:
        return ""
    
    last_prompt = pane_turn_prompts.get(pane_id, "").strip()
    if last_prompt:
        prompt_snippet = last_prompt[:35]
        marker = f"> {prompt_snippet}"
        pos = full_output.rfind(marker)
        if pos != -1:
            end_line = full_output.find("\n", pos)
            if end_line != -1:
                turn_text = full_output[end_line + 1:]
                next_prompt = turn_text.find("\n> ")
                if next_prompt != -1:
                    turn_text = turn_text[:next_prompt]
                return turn_text.strip("\n")
            else:
                return full_output[pos + len(marker):].strip("\n")
                
    baseline = pane_turn_baselines.get(pane_id)
    if baseline and full_output.startswith(baseline):
        turn_delta = full_output[len(baseline):].strip("\n")
        return turn_delta
    
    return full_output.strip("\n")

def submit_prompt_to_pane(pane_id, prompt_text):
    print(f"[HerdrBridge] Dispatching prompt/command to pane {pane_id}: {prompt_text}")
    
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
    focused_tab_id = snapshot.get("focused_tab_id", "")
    
    sessions_list = []
    session_messages = {}
    
    for tab in tabs:
        tab_id = tab.get("tab_id", "")
        label = tab.get("label", "")
        pane = next((p for p in panes if p.get("tab_id") == tab_id), {})
        pane_id = pane.get("pane_id", tab_id)
        
        agent_obj = next((a for a in agents if a.get("tab_id") == tab_id or a.get("pane_id") == pane_id), {})
        is_active_agent = bool(agent_obj.get("agent"))
        agent_name = agent_obj.get("agent") or pane.get("agent") or pane.get("terminal_title") or "zsh"
        cwd = pane.get("foreground_cwd") or pane.get("cwd") or ""
        dir_name = os.path.basename(cwd) if cwd else ""
        
        if label and not label.isdigit():
            title = label
        elif label and dir_name:
            title = f"{label} • {agent_name} ({dir_name})"
        elif label:
            title = f"{label} • {agent_name}"
        elif dir_name:
            title = f"{agent_name} • {dir_name}"
        else:
            title = f"{agent_name.upper()}"
            
        role = f"Herdr Agent in {dir_name or cwd}" if is_active_agent else f"Shell in {dir_name or cwd}"
        status = "ONLINE"
        
        sessions_list.append({
            "id": tab_id,
            "session_id": tab_id,
            "pane_id": pane_id,
            "title": title,
            "name": title,
            "label": label,
            "role": role,
            "status": status,
            "agent": agent_name,
            "cwd": cwd,
            "is_agent": is_active_agent,
            "model": "herdr/desktop"
        })
        
        output = get_pane_turn_output(pane_id)
        if output:
            session_messages[tab_id] = [{
                "id": f"msg_term_{tab_id}",
                "sessionId": tab_id,
                "sender": "AGENT",
                "content": output,
                "status": "SENT"
            }]
        
    return {
        "type": "sessions_list",
        "sessions": sessions_list,
        "active_sessions": sessions_list,
        "session_messages": session_messages,
        "sessionMessages": session_messages,
        "focused_tab_id": focused_tab_id,
        "count": len(sessions_list)
    }

async def live_terminal_watcher(websocket, client_ip):
    last_tab_hash = ""
    last_focused_tab = ""
    
    try:
        while True:
            await asyncio.sleep(0.12)
            
            payload = get_synced_sessions_payload()
            sessions = payload.get("sessions", [])
            tab_ids = ",".join(f"{s['id']}:{s['title']}" for s in sessions)
            focused_tab_id = payload.get("focused_tab_id", "")
            
            # 1. Check tab structure / names
            if tab_ids != last_tab_hash:
                last_tab_hash = tab_ids
                await websocket.send(json.dumps(payload))
            
            # 2. Check tab focus changes
            if focused_tab_id and focused_tab_id != last_focused_tab:
                last_focused_tab = focused_tab_id
                await websocket.send(json.dumps({
                    "type": "tab_focused",
                    "tab_id": focused_tab_id,
                    "session_id": focused_tab_id
                }))
            
            # 3. Live real-time output streaming per tab
            for session in sessions:
                tab_id = session.get("id")
                pane_id = session.get("pane_id") or tab_id
                
                turn_output = get_pane_turn_output(pane_id)
                if turn_output is None:
                    continue
                
                out_hash = hashlib.md5(turn_output.encode("utf-8")).hexdigest()
                if pane_last_streamed.get(tab_id) != out_hash:
                    pane_last_streamed[tab_id] = out_hash
                    
                    await websocket.send(json.dumps({
                        "type": "stream_turn_update",
                        "session_id": tab_id,
                        "content": turn_output,
                        "is_complete": False
                    }))
    except (asyncio.CancelledError, websockets.exceptions.ConnectionClosed):
        pass

async def handle_client(websocket):
    client_ip = websocket.remote_address
    print(f"[HerdrBridge] Client connected from {client_ip}")
    
    # 1. Send all sessions with their latest messages
    initial_payload = get_synced_sessions_payload()
    sessions = initial_payload.get("sessions", [])
    print(f"[HerdrBridge] Dispatched {len(sessions)} desktop tabs with content to client")
    await websocket.send(json.dumps(initial_payload))
    
    # 2. Stream latest content for each tab
    for session in sessions:
        tab_id = session.get("id")
        pane_id = session.get("pane_id") or tab_id
        output = get_pane_turn_output(pane_id)
        if output:
            pane_last_streamed[tab_id] = hashlib.md5(output.encode("utf-8")).hexdigest()
            await websocket.send(json.dumps({
                "type": "stream_turn_update",
                "session_id": tab_id,
                "content": output,
                "is_complete": True
            }))
            await asyncio.sleep(0.02)
    
    watcher_task = asyncio.create_task(live_terminal_watcher(websocket, client_ip))
    
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                msg_type = data.get("type", "") or data.get("action", "")
                
                # Tab sync / refresh pulls latest content across all tabs
                if msg_type in ("get_sessions", "list_sessions", "sync_tabs", "get_tabs", "refresh_tabs", "client_hello"):
                    print("[HerdrBridge] Tab refresh requested - broadcasting all tabs with latest content...")
                    payload = get_synced_sessions_payload()
                    await websocket.send(json.dumps(payload))
                    for session in payload.get("sessions", []):
                        tab_id = session.get("id")
                        pane_id = session.get("pane_id") or tab_id
                        out = get_pane_turn_output(pane_id)
                        if out:
                            await websocket.send(json.dumps({
                                "type": "stream_turn_update",
                                "session_id": tab_id,
                                "content": out,
                                "is_complete": True
                            }))
                            await asyncio.sleep(0.01)
                
                elif msg_type in ("select_tab", "focus_tab", "switch_tab"):
                    target_id = data.get("tab_id") or data.get("session_id") or ""
                    if target_id:
                        subprocess.run(["/Users/chetan/.local/bin/herdr", "tab", "focus", target_id], capture_output=True, text=True, timeout=2.0)
                        
                        payload = get_synced_sessions_payload()
                        session = next((s for s in payload.get("sessions", []) if s.get("id") == target_id), {})
                        pane_id = session.get("pane_id") or target_id
                        output = get_pane_turn_output(pane_id)
                        if output:
                            pane_last_streamed[target_id] = hashlib.md5(output.encode("utf-8")).hexdigest()
                            await websocket.send(json.dumps({
                                "type": "stream_turn_update",
                                "session_id": target_id,
                                "content": output,
                                "is_complete": True
                            }))
                
                elif msg_type in ("create_tab", "new_tab", "add_tab"):
                    label = data.get("label") or data.get("title") or ""
                    cmd = ["/Users/chetan/.local/bin/herdr", "tab", "create", "--focus"]
                    if label:
                        cmd.extend(["--label", label])
                    subprocess.run(cmd, capture_output=True, text=True, timeout=3.0)
                    
                    await asyncio.sleep(0.2)
                    payload = get_synced_sessions_payload()
                    await websocket.send(json.dumps(payload))
                
                elif msg_type in ("close_tab", "delete_tab", "remove_tab"):
                    target_id = data.get("tab_id") or data.get("session_id") or ""
                    if target_id:
                        payload = get_synced_sessions_payload()
                        session = next((s for s in payload.get("sessions", []) if s.get("id") == target_id or s.get("pane_id") == target_id), {})
                        actual_tab_id = session.get("id") or target_id
                        subprocess.run(["/Users/chetan/.local/bin/herdr", "tab", "close", actual_tab_id], capture_output=True, text=True, timeout=3.0)
                        
                        await asyncio.sleep(0.2)
                        updated_payload = get_synced_sessions_payload()
                        await websocket.send(json.dumps(updated_payload))
                
                elif msg_type in ("rename_tab", "rename_session"):
                    target_id = data.get("tab_id") or data.get("session_id") or ""
                    new_label = data.get("label") or data.get("title") or ""
                    if target_id and new_label:
                        subprocess.run(["/Users/chetan/.local/bin/herdr", "tab", "rename", target_id, new_label], capture_output=True, text=True, timeout=2.0)
                        await asyncio.sleep(0.1)
                        payload = get_synced_sessions_payload()
                        await websocket.send(json.dumps(payload))
                
                elif msg_type in ("get_tab_content", "get_history", "request_history"):
                    target_id = data.get("session_id") or data.get("tab_id") or ""
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == target_id), {})
                    pane_id = session.get("pane_id") or target_id
                    if pane_id:
                        output = get_pane_turn_output(pane_id)
                        if output:
                            await websocket.send(json.dumps({
                                "type": "stream_turn_update",
                                "session_id": target_id,
                                "content": output,
                                "is_complete": True
                            }))
                
                elif msg_type == "user_message":
                    session_id = data.get("session_id", "")
                    content = data.get("content", "")
                    
                    payload = get_synced_sessions_payload()
                    session = next((s for s in payload.get("sessions", []) if s.get("id") == session_id), {})
                    pane_id = session.get("pane_id") or session_id
                    
                    pane_turn_prompts[pane_id] = content
                    current_term = read_pane_terminal(pane_id, lines=800)
                    pane_turn_baselines[pane_id] = current_term
                    pane_last_streamed[session_id] = ""
                    
                    await websocket.send(json.dumps({
                        "type": "agent_status",
                        "session_id": session_id,
                        "status": "STREAMING",
                        "detail": "Running prompt..."
                    }))
                    
                    submit_prompt_to_pane(pane_id, content)
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
