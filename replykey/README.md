# ReplyKey — keyboard with an AI reply key

Type normally. Tap **✨ AI reply** and it reads the message on screen and writes a reply at your cursor.

## Install
1. Download `ReplyKey.apk` from the latest `replykey-*` GitHub release (built by `.github/workflows/replykey.yml`).
2. Open the **ReplyKey** app and follow the 3 steps:
   - Enable the keyboard and switch to it.
   - Enable **ReplyKey message reader** (Accessibility → Installed apps).
   - Paste a Claude API key (console.anthropic.com) and pick a model.

## How it reads the message
- An Android keyboard can only see its own text box, not the email above it.
- The accessibility service reads the visible text of the app under the keyboard.
- It also remembers the previous screen in the same app (memory only), so the email you opened before tapping Reply is included.
- Gmail collapses the quoted email behind `•••` in the reply box; the remembered screen covers that.
- Password fields are never read, and AI is disabled while typing in one.

## Panel actions
Insert · Retry · Shorter · Formal · Friendly · ✕

## Notes
- Default model: `claude-opus-5-5` (low effort for speed). Sonnet 5.5 / Haiku 5.5 selectable for faster replies.
- Uses the official Anthropic Java SDK.
- Screen text goes to Anthropic only when you tap ✨.
