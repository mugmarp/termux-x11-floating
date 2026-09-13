# v0.1.0-floating-kb

## What is it?
A persistent floating keyboard toggle button for Termux:X11.

## Features
- Persistent floating button (chat head style)
- Tap to toggle soft keyboard visibility
- Draggable with edge-snapping (left/right)
- Works without returning to main activity
- Target: Android 8+ (API 24–34)
- Zero external dependencies

## Installation
1. Download `termux-x11-universal-debug.apk`
2. Install (allow unknown sources)
3. Open Termux:X11, grant overlay permission
4. The floating button appears when connected to X11 session

## Known Issues
- Settings/Preferences may crash (ProGuard stripping PrefsProto classes)
- Will be fixed in next release

## Files
- `termux-x11-universal-debug.apk` — Standalone variant
- `termux-x11-sharedUid-debug.apk` — Shared UID variant (only works with Termux from GitHub)
- `termux-companion-packages` — .deb and .tar.xz companion packages

## Download
https://github.com/mugmarp/termux-x11-floating/actions/runs/33394362272