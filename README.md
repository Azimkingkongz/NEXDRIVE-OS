# 🚗 NEXDRIVE OS — Right-Hand Drive (RHD) Android Auto & Car Player Cockpit

![Platform](https://img.shields.io/badge/Platform-Android%20Auto%20%7C%20Android%20Car%20Player-amber?style=for-the-badge)
![Min SDK](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-0ea5e9?style=for-the-badge)
![License](https://img.shields.io/badge/License-Free%20%26%20Open%20Source-10b981?style=for-the-badge)

**NEXDRIVE OS** is a fast, ultra-lightweight (~3 MB APK, <50 MB RAM) custom automotive cockpit and dashboard designed specifically for **Right-Hand Drive (RHD)** vehicles (Malaysia, UK, Japan, Australia, Singapore, etc.).

It works seamlessly in **two modes** from a single universal APK:
1. **Android Auto / CarLink Mode (Via Smartphone):** Projects the full NEXDRIVE OS cockpit onto your factory OEM screen or CarLink/ZLink/TLink head unit (**100% Car Warranty Safe** — nothing is modified on the car's factory system).
2. **Standalone Android Car Player Mode (Direct Head Unit Install):** Runs directly as a full-screen dashboard launcher on any 7", 9", 10", or widescreen Android Car Player (Topway TS7/TS10/TS18, FYT/SYU UIS7862/8581, XYAuto T3L, Microntek, etc.).

---

## ✨ Key Features

- **🛞 Right-Hand Drive (RHD) Ergonomic Command Rail:**
  - The primary navigation bar (`🏠 COCKPIT`, `💾 PENDRIVE`, `🎤 LYRICS`, `🧭 WAZE`) is positioned along the **right edge of the screen** — closest to the driver's left hand in RHD cars for effortless, eyes-free tapping.
- **👆 Oversized Tactile Driving Buttons:**
  - Huge media transport controls (`⏮ PREV`, `⏸ PAUSE`, `NEXT ⏭`), a seekable progress bar, and an animated 4-bar audio spectrum visualizer.
- **💾 Dedicated USB Pendrive Folder & Song Explorer:**
  - Browse your USB flash drive folders on the left pane and tap large song rows on the right pane.
  - Supports loading real local USB audio folders (`MP3`, `FLAC`, `WAV`, `M4A`) and includes one-tap **🔀 Shuffle Folder**.
- **📡 Offline Internal FM Aerial Hardware Tuner + Preset Memory:**
  - Controls the car's internal MCU FM radio chip connected to your physical car aerial/antenna (**zero mobile data required**).
  - Supports Topway (`TS10`/`TS18`), FYT/SYU (`7862`/`8581`), XYAuto (`T3L`), Microntek (`MTC`), and includes a **`📻 Native FM`** shortcut to open the head unit's built-in radio scanner.
  - **Easy Favourite Presets:** Save any tuned frequency (`87.5 – 108.0 MHz`) into the 4 Favourite Slots either by **pressing & holding any slot for 1 second** (just like a traditional car radio) or tapping **`⭐ Save to Fav`**.
- **🚀 4 Customizable Big App Launcher Tiles (`2x2 Grid`):**
  - Pre-configured with **Waze**, **USB Music**, **Spotify**, and **YouTube** (with crisp vector SVG brand logos).
  - Tap **`⚙️ Customize 4 Apps`** anytime to swap slots with **BitChord**, **YouTube Music**, **Netflix**, **TikTok**, **WhatsApp**, or **Google Maps**.
- **🎤 Stage Karaoke Synced Lyrics:**
  - Automatically detects currently playing songs from Spotify, YouTube Music, or BitChord via Android `MediaSessionManager` and fetches real-time synced lyrics from **LRCLIB**.
- **🌡️ Symmetrical Top Telemetry Pod:**
  - Displays an oversized **Digital Clock & Date**, a **Live GPS Speedometer (`KM/H`)** with dynamic speed bar, and **Live Temperature (`°C`) + Reverse-Geocoded Area Location (`📍`)** directly underneath the temperature.
- **🎨 4 Cockpit Ambient Lighting Themes:**
  - Switch cockpit neon accents with one tap between **GT Amber-Cyan**, **Petronas Cyber Emerald**, **Type-R Crimson Red**, and **VIP Midnight Purple**.

---

## 📥 Download & Installation Guide

### Where to Download the APK
1. Go to the **[Releases](../../releases)** section on the right side of this GitHub page (or open the **[Actions](../../actions)** tab and click the latest workflow run).
2. Download **`NEXDRIVE-OS-v2.0.apk`**.

---

### 📱 Method 1: Connect via Smartphone + CarLink / Android Auto *(100% Car Warranty Safe)*

Use this method if your car uses a **Factory OEM Screen** with Android Auto, or if you connect your phone to your Android Player via **CarLink / ZLink / TLink**.
> **Note:** This method does **not** void your car warranty because the app is installed only on your phone — nothing is installed or modified on your car's head unit.

#### Step 1 — Install the APK on Your Android Phone
1. Download **`NEXDRIVE-OS-v2.0.apk`** to your Android phone and tap it to install.
   - **💡 If you see *"App blocked to protect your device"* (Google Play Protect / Samsung Auto Blocker):**
     - **Standard Android (Play Protect):** Tap **`More details`** on the popup ➔ tap **`Install anyway`** at the bottom (this appears because the APK is self-compiled on GitHub rather than downloaded from the Play Store).
     - **Samsung Phones (One UI 6+):** If there is no *"More details"* button, open phone **Settings** ➔ **Security and privacy** ➔ **Auto Blocker** ➔ temporarily turn it **Off**, then install the APK.
     - **Alternative:** Open **Google Play Store** ➔ tap your **Profile icon** (top right) ➔ **Play Protect** ➔ tap the **⚙️ Gear icon** (top right) ➔ temporarily pause **Scan apps with Play Protect** while installing.
2. Open the **MyCarDeck (NEXDRIVE OS)** app once on your phone:
   - Grant **Location (GPS)** permission when prompted.
   - Grant **Notification Access** permission so the app can read live song titles and synced lyrics from Spotify / YouTube Music.

#### Step 2 — Enable "Unknown Sources" in Android Auto *(One-Time Setup)*
Because NEXDRIVE OS is installed via APK outside the Google Play Store, you need to enable Developer Mode in Android Auto on your phone:
1. On your phone, open **Settings** ➔ search for and open **Android Auto**.
2. Scroll all the way to the bottom and find **Version**.
3. **Tap on "Version" 10 times rapidly** until a popup asks *"Allow development settings?"* ➔ tap **OK**.
4. Tap the **three dots menu (`⋮`)** at the top-right corner of the Android Auto settings screen ➔ select **Developer settings**.
5. Scroll down and **check ✅ `Unknown sources`**.
6. *(Optional)* Tap **Application Mode** in Developer settings and set it to **Developer**.

#### Step 3 — Connect Your Phone to Your Car
1. Start your car and connect your phone via **CarLink** (wireless/cable) or plug your USB cable into your car's **Android Auto** USB port.
2. On your car screen, tap the **App Launcher icon (`⊞`)** on the bottom Android Auto dock bar.
3. Tap **MyCarDeck (NEXDRIVE OS)** — the full RHD cockpit will appear on your car screen!

---

### 📺 Method 2: Install Directly on an Android Car Player Head Unit *(Standalone Mode)*

Use this method if you have an aftermarket **Android Car Player** (7", 9", 10", or widescreen) and want NEXDRIVE OS to run directly on the head unit with direct access to your **car's USB Pendrive port** and **internal FM Aerial radio tuner**.

1. Copy **`NEXDRIVE-OS-v2.0.apk`** onto a USB Pendrive (or download it directly using Chrome on your Android Car Player).
2. Plug the USB Pendrive into your Android Car Player's USB port.
3. Open the **File Manager** app on your Android Car Player, navigate to your USB Pendrive, and tap **`NEXDRIVE-OS-v2.0.apk`** to install it.
4. Open **MyCarDeck (NEXDRIVE OS)** and grant **Location (GPS)** permission.
5. **To play USB Pendrive songs:** Tap **`💾 USB Music`** (or **`💾 PENDRIVE`** on the right rail) ➔ tap **`📂 Load Real USB Pendrive Folder`** ➔ select your USB drive music folder.
6. **To use Waze / Spotify / YouTube in Standalone Mode:** Make sure Waze, Spotify, or YouTube are installed from the Google Play Store on your Android Player so tapping the big launcher tiles opens the native apps directly.

---

## 🛠️ How to Use Key Controls

| Feature | How to Use |
| :--- | :--- |
| **Save FM Radio Station to Favourites** | Tune to your desired frequency using **`◀ -0.1 MHz`** / **`+0.1 MHz ▶`**, then either **press and hold any of the 4 preset slots for 1 second**, OR tap **`⭐ Save to Fav`** and tap a slot (`1–4`). |
| **Open Built-in Car Radio Scanner** | Tap **`📻 Native FM`** on the FM Aerial card to launch your Android Player's factory MCU radio app for auto-scanning local stations. |
| **Customize the 4 Big App Tiles** | Tap **`⚙️ Customize 4 Apps`**, choose `Slot 1` to `Slot 4`, and tap any app from the 10-app catalog. Your layout is saved automatically. |
| **Browse & Shuffle USB Folders** | Tap **`💾 PENDRIVE`** on the right rail (or tap the spinning disc `💿` on the audio deck), pick a folder on the left, and tap any song on the right (or tap **`🔀 Shuffle Folder`**). |
| **Change Cockpit Ambient Color** | Tap **`🎨 THEME`** at the bottom of the right-hand command rail to cycle through the 4 neon cockpit color themes. |

---

## 📂 Project Structure

```text
MyCarDeck/
├── .github/workflows/
│   └── build-apk.yml              # Automated GitHub Actions cloud APK builder & Release publisher
├── app/
│   ├── build.gradle.kts           # Android app module configuration (minSdk 24, Car App Library 1.4.0)
│   └── src/main/
│       ├── AndroidManifest.xml    # Declares Launcher Activity, MediaTrackerService & Android Auto Service
│       ├── assets/dashboard/
│       │   └── index.html         # NEXDRIVE OS RHD Cockpit UI (HTML5 / CSS3 / Vanilla JS)
│       └── java/my/cardeck/app/
│           ├── MainActivity.kt        # Fullscreen WebView host + Native App Launcher + Hardware FM MCU Bridge
│           ├── MediaTrackerService.kt # NotificationListenerService + LRCLIB synced lyrics engine
│           └── auto/
│               ├── MyCarDeckAutoService.kt # Android Auto CarAppService entry point
│               └── MyCarDeckCarScreen.kt   # VirtualDisplay + SurfaceCallback projection for Android Auto
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🧑‍💻 Building from Source (Optional)

If you have Android Studio or JDK 17 + Gradle installed locally:

```bash
gradle assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`
