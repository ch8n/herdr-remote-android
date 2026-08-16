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

## ⚙️ Configuration

1. **OpenRouter AI Key**:
   - Open **Settings** (⚙️ top right).
   - Paste your OpenRouter API key (`sk-or-v1-...`).
   - Tap **Test API Key** to verify connection and load all available AI models.

2. **Connecting to Herdr Node**:
   - **Autonomous Simulation Mode**: Enable *Autonomous Mock Simulation* in Settings to test all agent features, tool simulations, and reasoning without a backend.
   - **Live Backend / Tailscale Mode**: Toggle off Mock Mode, paste your WebSocket URL (e.g., `ws://100.x.y.z:8080/herdr/ws`), and tap **Save Preferences**.

---

## 🔒 Security & Privacy

- All API keys and server connection URLs are stored **locally on-device** in Android `SharedPreferences`.
- No telemetry, analytics, or third-party tracking scripts are bundled.
- All Tailscale network traffic is end-to-end encrypted via WireGuard.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
