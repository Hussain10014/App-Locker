# ZenLock - Complete Phone Transfer & Lockdown Guide 📱🔒

This guide gives you two complete ways to use ZenLock on your phone with **100% unclosable lock mode and hidden notifications**:

---

## ⚡ Option 1 (Instant - No APK Compilation Needed): Android "App Pinning" + DND

You can get an unclosable lock and hide notifications **right now** on your Android phone using Android's built-in system security features.

### Step 1: Add ZenLock to Your Phone's Home Screen
1. On your PC, run `python server.py`.
2. On your phone (connected to same Wi-Fi), open Chrome and enter the address (e.g. `http://192.168.x.x:8000`).
3. Tap the three dots (**⋮**) in Chrome &rarr; Tap **"Add to Home Screen"** or **"Install app"**.
4. An independent app icon now appears on your phone's home screen. **It will no longer look or feel like a browser tab.**

### Step 2: Enable "App Pinning" on your Phone (One-Time Setup)
All Android phones have a built-in kiosk security feature called **App Pinning (Screen Pinning)**:
- Open your phone's **Settings**.
- Search for **"Pin"** or go to **Security & Privacy &rarr; More security settings &rarr; App Pinning (Screen Pinning)**.
- Toggle it **ON**.

### Step 3: Engaging True Lockdown (Unclosable + Hidden Notifications)
1. Pull down your phone's notification panel and tap **Do Not Disturb (DND)**.
   * *This silences and completely hides all incoming message banners and alerts.*
2. Open **ZenLock** from your home screen.
3. Select your desired time (e.g. 25 min) and ensure **"Unbreakable Lock Mode"** is turned **ON**.
4. Tap **"Engage Lock"**.
5. Swipe up slightly to view **Recent Apps / App Switcher** &rarr; Tap the **ZenLock circular icon** above the app preview &rarr; Tap **"Pin"** (or **"Pin this app"**).

> [!IMPORTANT]
> **What Happens When Pinned?**
> - You **cannot** swipe away or close the app.
> - The **Home button is disabled**.
> - The **Recent Apps button is disabled**.
> - The **notification bar is locked and cannot be pulled down**.
> - Emergency exit is disabled by ZenLock's Unbreakable Mode!

---

## 📦 Option 2: Build the Native Android APK (`ZenLock.apk`) via GitHub Actions

If you want a standalone Android `.apk` file that asks for permissions and hides notifications automatically:

We have included a pre-configured **GitHub Actions automated cloud builder** (`.github/workflows/build-apk.yml`) in this folder. You don't need Android Studio or Java installed on your computer.

### Step 1: Put the Code on GitHub
1. Go to [github.com](https://github.com) and create a free new repository named `zenlock`.
2. Push or upload all files from this `Phone locker` folder to your new repository.

### Step 2: GitHub Automatically Builds the APK
1. As soon as the files are pushed, click on the **"Actions"** tab at the top of your GitHub repository.
2. You will see a workflow named **"Build ZenLock Android APK"** running.
3. In about **90 seconds**, it will finish with a green checkmark (✅).

### Step 3: Download and Install on Your Phone
1. Open the completed workflow run.
2. Under **Artifacts**, tap **"ZenLock-App"** to download the `app-debug.apk`.
3. Open the downloaded file on your phone and tap **Install**.

### Step 4: First Launch Permissions
1. Open **ZenLock** on your phone.
2. Tap **"Grant"** on the System Protection banner.
3. Android Settings will open to **"Do Not Disturb Access"**. Find **ZenLock** and switch it **ON**.
4. Now, every time you tap **"Engage Lock"**:
   - Notifications are automatically hidden.
   - Screen Pinning is automatically engaged.
   - You cannot quit until the timer completes!

---

## 🛠️ Summary of Built Files

- `index.html`, `style.css`, `app.js`: Complete mobile focus chamber UI with Unbreakable Mode and native bridge.
- `android/`: Full Android Studio project containing `MainActivity.java` and `WebAppInterface.java`.
- `server.py`: Local Python server for Wi-Fi PWA testing.
- `.github/workflows/build-apk.yml`: Cloud compilation pipeline for generating `ZenLock.apk`.
