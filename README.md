# KRS Gold & Silver — Live MCX

Android Jetpack Compose application for live 1-second MCX Gold & Silver rates, physical bullion availability, home screen widgets, official announcements, and database-driven OTA app updates.

## Download & Install APK on Your Phone

> **Note:** Clicking **Code → Download ZIP** on GitHub downloads the **Android source code**, not an installable `.apk` file.

To get the installable `.apk` file:

1. **In AI Studio:** Use the top menu **Export / Download APK** to download the latest compiled APK directly.
2. **Via GitHub Releases (Public Phone Link):**
   - After pushing from AI Studio (**Push to GitHub**), open this repository on **GitHub.com → Actions → New workflow → set up a workflow yourself**.
   - Copy the workflow from `docs/android-apk-release.yml` into `.github/workflows/android-apk-release.yml` and commit it.
   - Run the workflow (or push a commit) to publish **`KRS-Gold-Silver-Live-MCX.apk`** under **Releases → Latest**, giving you a direct `.../releases/latest/download/KRS-Gold-Silver-Live-MCX.apk` link for all users.

## Building Locally from GitHub Source ZIP

If you download the source ZIP from GitHub and build with Gradle 9.3.1 + JDK 17:

```bash
base64 -d debug.keystore.base64 > debug.keystore
gradle assembleRelease --no-daemon
```

The signed APK is generated at `app/build/outputs/apk/release/app-release.apk`.
