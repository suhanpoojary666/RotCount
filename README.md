# RotCount

A minimal, offline Android app that counts Instagram Reels and Home Feed scrolls with a draggable floating pill and clean activity stats.

---

## Features & Functionality

- **Live Magnetic Pill:**
  - Floats on your screen while browsing Instagram.
  - Can be dragged anywhere on screen.
  - Magnetically snaps to whichever of the 4 borders (top, bottom, left, right) is nearest when released.
  - Automatically hides when you leave Instagram or lock your phone.
- **Selective Scroll Counting:**
  - Counts only forward scrolls in **Reels** and **Home feed** (+1 for each post you stop on).
  - Rapid fling-and-stop counts +1 for the reel you settle on.
  - Re-watching earlier reels (scrolling back up and down) is **never recounted**.
  - Completely ignores DMs, chat threads, comment sheets, explore grid, search, and profiles.
- **Activity Stats:**
  - **Day:** Hourly breakdown of your scrolls.
  - **Week:** 7-day bar chart with past week history.
  - **Year:** Full-year activity heatmap tape.
  - Swipe horizontally anywhere across the screen to switch between Day, Week, and Year.

---

## Privacy

- **100% Offline:** The app does not request or have internet permission (`android.permission.INTERNET` is completely omitted).
- **Private:** Runs an Accessibility Service purely to notice scroll gestures and container sizes. It **never** reads your private messages, on-screen text, passwords, or personal data.
- **Local Storage:** Everything is saved locally on your phone in an offline database. Uninstalling the app permanently removes all data.

---

## Installation & Setup

1. **Download the APK** from the Releases section.
2. **If Google Play Protect blocks installation:**
   - Because this is an independent sideloaded APK, Play Protect might show a warning.
   - To bypass: Open the **Google Play Store** &rarr; tap your **Profile icon** (top-right) &rarr; **Play Protect** &rarr; tap the **Settings gear icon** (top-right) &rarr; turn off / pause **"Scan apps with Play Protect"**.
   - Install the RotCount APK, then you can turn Play Protect back on.
3. **If Accessibility is greyed out (Android 13+):**
   - Go to your phone's **Settings** &rarr; **Apps** &rarr; **RotCount**.
   - Tap the **three dots (:vertical_ellipsis:)** in the top-right corner.
   - Select **"Allow restricted settings"** and confirm with your fingerprint/PIN.
4. **Enable the Counter:**
   - Open **RotCount** &rarr; tap **Open Settings**.
   - Turn on **RotCount Accessibility Service**.
5. Open Instagram and your floating counter pill will appear!
