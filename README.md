# FocusLock

Android app that blocks distracting apps during timed focus sessions. Requires a PIN to open, to unlock blocked apps mid-session, and to remove device admin (uninstall protection).

---

## How to build & install (no USB, no Play Store)

### 1 — Fork / create a GitHub repo and push this project

```bash
# In this directory:
git init
git add .
git commit -m "Initial FocusLock project"
git remote add origin https://github.com/YOUR_USERNAME/focuslock.git
git push -u origin main
```

### 2 — Wait for GitHub Actions to build

Go to `https://github.com/YOUR_USERNAME/focuslock/actions` — the **Build Debug APK** workflow runs automatically on every push to `main`. A green checkmark means the APK is ready.

### 3 — Download the APK on your phone

Direct download URL (works once the first build completes):

```
https://github.com/YOUR_USERNAME/focuslock/releases/latest/download/FocusLock.apk
```

Open this URL in your phone's browser.

### 4 — Install the APK

1. When prompted, tap **Settings → Allow from this source** (or go to Settings → Apps → Special app access → Install unknown apps → your browser → Allow)
2. Tap the downloaded file and install

---

## First-run setup (in order)

| Step | What to do |
|------|-----------|
| 1 | Open FocusLock — you'll be asked to set a **4-digit PIN**. Remember it; there is no recovery. |
| 2 | Tap **Enable Accessibility Service** → find *FocusLock App Blocker* → toggle it on. |
| 3 | Tap **Activate Device Admin** → confirm. This prevents accidental uninstall during sessions. |
| 4 | Tap the checkboxes next to apps you want to block, then tap **Save Selection**. |
| 5 | Pull down the notification shade — tap **Start Lock (1hr)** to begin a session. |

---

## How it works

| Feature | Implementation |
|---------|---------------|
| Persistent notification | `LockForegroundService` (foreground service, SPECIAL_USE type) |
| 1-hour countdown | Handler-based minute ticker inside the service |
| App list | `PackageManager.getInstalledApplications` filtered to non-system apps |
| PIN storage | SHA-256 hash in `SharedPreferences` |
| App blocking | `AccessibilityService` watching `TYPE_WINDOW_STATE_CHANGED` events |
| PIN overlay | `BlockOverlayActivity` launched with `FLAG_ACTIVITY_NEW_TASK` |
| Uninstall protection | Device Admin via `DevicePolicyManager` |
| Boot persistence | `BootReceiver` restarts the service after reboot |

---

## Notes

- The APK is a **debug build** — no signing keystore required, installable from unknown sources.
- The Device Admin registration prevents direct uninstall; you must deactivate admin first (Settings → Security → Device Admin Apps → FocusLock → Deactivate), then uninstall.
- `compileSdk` and `targetSdk` are set to **35** (Android 15). All features work on Android 8+ (API 26+).
- The accessibility service runs in the background continuously — this is necessary for app blocking to work. Battery impact is minimal (event-driven, not polling).
