# AI Dock — one-tap AI widgets for Android

**Download:** [`dist/AIDock.apk`](../dist/AIDock.apk) → install on your phone (allow "install unknown apps"). Android 8.0+.

## Widgets
- **All chats** (resizable grid): ChatGPT, Claude, Gemini, Perplexity, Copilot, Meta AI, DeepSeek, Grok, Le Chat, NotebookLM.
  Opens the app if installed, otherwise the website. Real app icons when installed.
- **Quick actions** (4×2): Type · Speak · Clipboard · Lens · Summarise · Explain · Translate · Fix text.
  The last four send whatever you copied to your chosen AI with a ready-made instruction.
- **Voice** (2×1): speak a question → it lands in your chosen AI; or open the phone's assistant.

## Also
- Quick Settings tile **Speak to AI**.
- Select text anywhere → menu → **Ask AI**; or share text → **Ask AI**.
- In the app: pick where questions go, choose and order the dock, add widgets with one tap.

## How prompts are handed over
1. AI app installed → Android share intent (text appears in its composer).
2. Otherwise → web URL with the prompt prefilled (ChatGPT, Claude, Perplexity, Copilot, Grok, Le Chat).
3. Otherwise (Gemini/DeepSeek/Meta AI web) → prompt copied to clipboard, app/site opened, paste it.

No accounts, no network permission, no API keys. Settings stay on the phone.

## Build
`./build.sh` — same Gradle-free pipeline as `../android/build.sh` (aapt2 → javac → dx → apksig). Output: `../dist/AIDock.apk`.
