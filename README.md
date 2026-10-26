# RotCount v1.0.0

A minimal, offline Android application that counts how many Instagram Reels you scroll each day, featuring a live Dynamic Island–styled floating counter pill and Apple HIG–inspired Day and Week activity stats.

---

## What It Does

- **Instagram Reels Only**: Counts full-screen Instagram Reels.
- **Home Feed Ignored**: Standard Home feed posts and Stories are completely ignored and never counted.
- **No Overcounting on Re-watch**: Scrolling back to previous reels and scrolling forward through already-seen reels does not increment the count.
- **Fast Fling Support**: Rapid swipes that jump across reels count as +1 when you settle on a reel, not +N.
- **Direct Message & Comment Protection**: Chat scrolls and comment sheets are automatically detected and blocked.
- **Midnight Rollover**: Automatically resets daily counts at midnight and starts a new day at local midnight.

> **Note**: YouTube Shorts support is not included in this release.

---

## Installation & Setup

1. **Uninstall Previous Debug Builds**: If you previously installed an early debug build of rotcount, uninstall it first to avoid certificate mismatch errors.
2. **Install APK**: Download `rotcount-v1.0.0.apk` and tap to install. When prompted by Android, tap **Settings** and allow **Install unknown apps** for your browser or file manager.
3. **Android 13+ Restricted Settings**: On Android 13 and newer, sideloaded apps require explicit authorization before accessibility services can be activated:
   - Go to device **Settings > Apps > RotCount**.
   - Tap the **three dots (⋮)** in the top right corner.
   - Tap **Allow restricted settings** and confirm with your device PIN or fingerprint.
4. **Enable Accessibility Service**:
   - Open **RotCount** and tap **Open Settings** (or go to **Settings > Accessibility > Installed apps > RotCount**).
   - Turn the toggle **ON** and confirm.
5. **Battery Optimization**:
   - In **Settings > Apps > RotCount > Battery**, set usage to **Unrestricted** so Android does not kill the overlay service in the background.

---

## Privacy & Permissions

RotCount is built with zero-telemetry local privacy:

> *"Reads only which app is open and scroll position, never text, messages or content; no internet permission, data stays on your phone."*

- **Zero Network Access**: `android.permission.INTERNET` is completely omitted from the app manifest. No telemetry, analytics, or background network calls can ever occur.
- **Local Storage Only**: Daily and hourly counts are stored entirely on your device in a private SQLite database (`rotcount.db`).
- **Backup Protected**: `android:allowBackup="false"` is set to prevent private counting data from being extracted via ADB backup.

---

## Known Limits

- **Instagram Updates**: Counting heuristics rely on Instagram's view hierarchy and clip identifiers. Significant architectural changes in future Instagram updates could require an app update.
- **Battery Saver / OEM Task Killers**: Aggressive battery managers (e.g. Xiaomi MIUI/HyperOS, Huawei EMUI, Samsung Device Care) may sleep background accessibility services if not set to *Unrestricted*.

---

## Release Verification & Integrity

- **APK Filename**: `rotcount-v1.0.0.apk`
- **Package Name**: `com.suhan.reelcounter`
- **Version Name**: `1.0.0`
- **Version Code**: `1`
- **APK Size**: ~1.1 MB (optimized & minified release build)

### APK SHA-256 Checksum
```
94520e800af8fa4fb1415685b1d0f60d33139abf5fc70869d1359ed3d063d57d
```

### Signing Certificate Fingerprints
- **Signer DN**: `CN=RotCount, OU=Mobile, O=RotCount, L=San Francisco, ST=California, C=US`
- **Key Type**: RSA 4096-bit (Validity: 10,000 days)
- **SHA-256 Fingerprint**:
  ```
  E2:0A:78:B5:A0:50:54:4F:61:54:6B:7A:17:45:34:38:C2:4B:9F:44:D8:13:27:92:2B:52:D9:CD:62:2F:6B:4B
  ```
- **SHA-1 Fingerprint**:
  ```
  F3:E9:EC:4F:4D:4F:E9:E2:FB:72:0D:6A:5D:12:E6:29:4E:B8:6F:84
  ```
