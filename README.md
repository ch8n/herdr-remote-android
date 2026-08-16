# Herdr Remote Android

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Tailscale](https://img.shields.io/badge/Network-Tailscale%20WireGuard-202A36?logo=tailscale&logoColor=white)](https://tailscale.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An impeccable, modern Android client for **Herdr** autonomous agent cluster interfacing. Designed with a fluid Telegram-inspired chat UX, multi-session agent tabs, real-time voice rephrasing via OpenRouter AI, Tailscale private node connectivity, and actionable system notifications with inline direct reply.

---

## ✨ Key Features

### 🗂️ Multi-Session Agent Tabs
- **Tabbed Agent Workspace**: Spawn, switch, and manage independent agent sessions (Orchestrator, Research Specialist, Coding Agent, DevOps & Infra).
- **Agent Header & Status Rings**: Live visual indicator of agent lifecycle state (`Online`, `Thinking`, `Executing Tool`, `Streaming`).
- **Markdown & Code Rendering**: Monospace syntax highlighting with 1-tap code copy.

### 🎙️ Speech-to-Text & AI Voice Rephrase
- **Android SpeechRecognizer Integration**: Real-time microphone capture with live audio amplitude (`rmsDb`) visualizer.
- **OpenRouter AI Auto-Rephrase**: Automatically removes verbal fillers (*"um"*, *"uh"*, *"you know"*) and transforms spontaneous speech into crisp, structured agent prompts.
- **Side-by-Side Comparison Modal**: Review original spoken transcript vs AI-polished prompt before sending.

### 🔍 400+ OpenRouter Model Selector
- **Dynamic Model Fetching**: Fetches live models directly from the OpenRouter API.
- **Search & Filter Dropdown**: Live filtering across model IDs, provider tags (`Gemini`, `DeepSeek`, `Llama`, `Claude`, `Mistral`, `Qwen`), context lengths, and `FREE` badges.
- **Persistent Storage**: API keys and model preferences are stored securely in local `SharedPreferences` with 1-tap clipboard copy/paste.

### 🌐 Tailscale Private Network Support
- **Zero-Config Tailnet Connection**: Connect directly to your remote Herdr server or agent daemon over private WireGuard Tailscale IPs (`100.x.y.z`) or MagicDNS (`ws://macbook.ts.net:8080/herdr/ws`).
- **Live VPN & Interface Detection**: In-app indicator showing active Tailscale connection status.
- **1-Tap App Switcher**: Automatically opens the Tailscale Android app or Play Store page if not yet installed.

### 🔔 Actionable System Notifications & Direct Reply
- **Task Completion Alerts**: System notifications when an agent finishes thinking or executing tasks.
- **Inline Direct Reply (`RemoteInput`)**: Reply back to any session directly from the Android notification shade.
- **Permission Confirmation Actions**: Elevated commands (e.g. bash execution, deployment) generate notifications with **`✅ Allow`** and **`❌ Deny`** quick actions.
- **Multi-Choice Smart Replies**: Dynamic suggestion chips and multi-choice action buttons.

### 📎 Rich Media & Presets
- **Image & PDF Attachments**: Built-in photo picker, document picker, and sample spec presets.
- **Expandable Reasoning Traces**: Collapsible thought / reasoning blocks (`AgentThoughtCard`) and tool execution telemetry (`ToolExecutionCard`).

---

## 🏗️ Architecture & Tech Stack

- **UI Toolkit**: Jetpack Compose with Material Design 3 (Dark Theme First, HSL tailored accents).
- **Language**: Kotlin 2.0.21.
- **Networking**: OkHttp 4.12.0 (WebSocket + REST client), Coil 2.7.0 (Image loading).
- **Data & State**: Kotlin Coroutines, `StateFlow`, `SharedFlow`, Android Architecture Components (`ViewModel`).
- **Storage**: Android `SharedPreferences` for user preferences and API credentials.
- **Speech**: Android `SpeechRecognizer` with permission handling.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer) / JDK 17+
- Android SDK 35 (minSdk 26)
- Android device or emulator running Android 8.0+

### Build & Run
```bash
# Clone the repository
git clone https://github.com/ch8n/herdr-remote-android.git
cd herdr-remote-android

# Build Debug APK
./gradlew assembleDebug

# Install on connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

---

## 🖥️ Herdr Remote Control Setup & Guide

Herdr Remote connects the Android app directly to your desktop **Herdr** autonomous coding agent sessions and terminal panes in real-time over local Wi-Fi, LAN, or Tailscale WireGuard VPN.

### 📐 Architecture Overview

```
┌───────────────────────────┐         ┌──────────────────────────────┐         ┌─────────────────────────────────┐
│   Android Phone (App)     │  Wi-Fi  │   herdr-bridge.py (Daemon)   │  Local  │    Herdr Desktop / Agents       │
│  (100.122.158.96:42689)   │ ──────> │      (0.0.0.0:8765)          │ ──────> │ (~/.config/herdr/herdr.sock)    │
│  WebSocket Client         │ Tailnet │  WebSocket ⇄ Socket Bridge   │  Unix   │ Terminal Panes & Shells         │
└───────────────────────────┘         └──────────────────────────────┘ Socket  └─────────────────────────────────┘
```

---

### ⚡ Quick Start: Running the Remote Bridge

The repository includes a management CLI (`scripts/herdr-remote`) and Python daemon (`herdr-bridge.py`).

#### 1. Prerequisites (on your Mac / Host Machine)
- Python 3.9+ with `websockets` library:
  ```bash
  pip3 install websockets
  ```
- **Herdr** CLI installed and running:
  ```bash
  herdr session list
  ```

#### 2. Start the Remote Bridge Daemon
Run the management script from the project root:

```bash
# Start bridge daemon in the background (binds to ws://0.0.0.0:8765)
./scripts/herdr-remote start

# Check status & connected socket
./scripts/herdr-remote status

# View live streaming terminal logs
./scripts/herdr-remote logs
```

#### 3. CLI Command Reference

| Command | Description |
|---------|-------------|
| `./scripts/herdr-remote start` | Starts the WebSocket daemon in background |
| `./scripts/herdr-remote stop` | Stops the running daemon |
| `./scripts/herdr-remote restart` | Restarts the bridge daemon |
| `./scripts/herdr-remote status` | Checks port `8765` and Unix socket connectivity |
| `./scripts/herdr-remote logs` | Tails live bridge event logs |
| `./scripts/herdr-remote foreground` | Runs the bridge in interactive foreground mode |
| `./scripts/herdr-remote install-service` | Installs macOS `LaunchAgent` to auto-start on boot |
| `./scripts/herdr-remote uninstall-service` | Removes macOS `LaunchAgent` service |

#### 4. Optional: Zero-Touch Auto-Start (macOS LaunchAgent)
To have the bridge start automatically on macOS login without running terminal commands:

```bash
./scripts/herdr-remote install-service
```

---

---

### 📱 Connecting the Android App

1. Open **Settings** (⚙️ top right in the Android app).
2. Toggle **Mock Mode** to `OFF`.
3. Set the **WebSocket URL**:
   - **Over Local Wi-Fi**: `ws://<YOUR_MAC_LOCAL_IP>:8765` (e.g., `ws://192.168.1.100:8765`)
   - **Over Tailscale VPN**: `ws://<YOUR_MAC_TAILSCALE_IP>:8765` (e.g., `ws://100.122.158.96:8765`)
4. Tap **Save Preferences**.
5. The app will immediately establish a live connection:
   - All active desktop tabs and agent names will appear in the top tab bar.
   - Live stdout/stderr terminal streams and AI responses will stream in real time.
   - Switching, opening, or closing tabs on the phone automatically mirrors on your desktop.

---

### 🌐 Mini Tailscale Setup Guide (Remote Access from Anywhere)

[Tailscale](https://tailscale.com) creates a secure, encrypted WireGuard mesh VPN between your Mac and Android phone, allowing you to control your desktop agents from cellular data or outside networks with zero port forwarding.

#### 1. Setup on Mac (Host)
1. Install Tailscale:
   ```bash
   brew install --cask tailscale
   # Or download from https://tailscale.com/download/mac
   ```
2. Open Tailscale, log in, and find your Mac's 100.x Tailscale IP:
   ```bash
   tailscale ip -4
   # Example output: 100.122.158.96
   ```

#### 2. Setup on Android (Phone)
1. Install **Tailscale** from the [Google Play Store](https://play.google.com/store/apps/details?id=com.tailscale.ipn).
2. Sign in with the **same account** used on your Mac and toggle the VPN switch to **Active**.
3. Tap on your Mac's node in the list to verify connectivity.

#### 3. Connect in Herdr Remote App
1. Open **Herdr Remote** on your phone.
2. Tap **Settings (⚙️)**.
3. Paste the WebSocket URL using your Mac's Tailscale IP or MagicDNS hostname:
   ```text
   ws://100.122.158.96:8765
   ```
4. Tap **Save Preferences**. You are now securely connected anywhere in the world!

---

---

## 🔒 Security & Privacy

- All API keys and server connection URLs are stored **locally on-device** in Android `SharedPreferences`.
- No telemetry, analytics, or third-party tracking scripts are bundled.
- All Tailscale network traffic is end-to-end encrypted via WireGuard.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
