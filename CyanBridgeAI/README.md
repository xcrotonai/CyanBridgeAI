# CyanBridge AI 🥽

A smarter Android app for **HeyCyan smart glasses** with multi-AI integration and live translation.

## Features
- 🔷 **Gemini Flash 2.0** (free tier available)
- 🟢 **ChatGPT GPT-4o** (paid, ~$0.01/photo query)
- 🟠 **Claude Sonnet** (paid, ~$0.01/photo query)
- 📷 Point glasses camera → AI describes what you see
- 🌐 Live translation in 15 languages with OCR from glasses camera
- 🔒 API keys stored encrypted on device
- 🦷 BLE connection to HeyCyan glasses

## Quick Start

### 1. Clone this repo
```
git clone https://github.com/YOUR_USERNAME/CyanBridgeAI.git
```

### 2. Open in Android Studio
File → Open → select the `CyanBridgeAI` folder → OK

### 3. Run on your Pixel
- Enable Developer Mode on your Pixel (tap Build Number 7 times in Settings → About Phone)
- Enable USB Debugging (Settings → Developer Options → USB Debugging)
- Connect via USB, tap "Allow" on your phone
- Press the ▶ Play button in Android Studio

### 4. Add your API keys
Once the app is running on your phone:
- Tap **Settings** tab at the bottom
- Paste your Gemini key (get free at [aistudio.google.com](https://aistudio.google.com))
- Optionally add OpenAI and/or Anthropic keys
- Tap **Save Keys Securely**

### 5. Connect your glasses
- Put HeyCyan glasses in pairing mode
- Tap **Devices** tab → **Scan for Glasses**
- Tap your glasses when they appear
- Go to **Control** tab and start using it!

## Getting API Keys

| Provider | Cost | URL |
|----------|------|-----|
| Gemini | **Free tier** (most users) | [aistudio.google.com](https://aistudio.google.com) |
| OpenAI / GPT-4o | ~$0.01/photo | [platform.openai.com](https://platform.openai.com) |
| Claude / Anthropic | ~$0.01/photo | [console.anthropic.com](https://console.anthropic.com) |
| Google Translate | Optional, 500k chars free/month | [console.cloud.google.com](https://console.cloud.google.com) |

## Architecture

```
MainActivity
├── ControlFragment   — glasses controls, battery, capture buttons
├── DevicesFragment   — BLE scanner and connection
├── ChatFragment      — AI conversation with photo support
├── TranslateFragment — live translation + OCR
└── SettingsFragment  — API key management

Core classes:
├── BleManager        — Bluetooth LE connection to HeyCyan glasses
├── AiRouter          — Routes requests to Gemini/OpenAI/Claude
├── LiveTranslator    — Translation + OCR + text-to-speech
├── MainViewModel     — App state (survives screen rotation)
└── SecurePrefs       — Encrypted key storage
```
