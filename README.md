# Hotspot WebKit PS4-PS5

[![Latest Release](https://img.shields.io/badge/Release-v1.2-blue.svg)](https://github.com/Fazzdevv/hotspot-webkit/releases/tag/v1.2)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B%20%28Android%208.0%2B%29-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20%28Android%2014%29-blue.svg)](https://developer.android.com)
[![Non-Root](https://img.shields.io/badge/Permissions-Non--Root%20Compatible-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An Android application that turns your smartphone into an offline exploit host and remote package installer for PlayStation 4 and PlayStation 5 over Wi-Fi hotspot, without requiring root access.

---

## Features

- **Offline Exploit Host**: Host and serve jailbreak exploits directly to PS4 and PS5 via User's Guide or browser without an internet connection.
- **PS4 PKG Sender**: Send and install game packages, updates, and DLC files (`.pkg`) wirelessly to PS4 from phone storage via GoldHEN BinLoader (Port 9090).
- **Live Transfer Console**: Real-time activity log showing connection status, payload injection progress, and download activity.
- **Web Ripper**: Clone and cache online exploit websites directly to your phone for offline use.
- **Dual Server Modes**: Separate WebKit Exploit and PKG Sender modes with automatic server management.
- **Bilingual Interface**: In-app language toggle between English and Indonesian.
- **Background Service**: Persistent background service keeps the server active even when the screen is turned off.

---

## How to Use

### WebKit Exploit Mode
1. Turn on **Portable Hotspot** on your Android phone.
2. Open the app on the **WebKit Exploit** tab and import your exploit files or use Web Ripper.
3. Tap **Start WebKit Server**.
4. On your PS4 or PS5:
   - Connect to the phone's Wi-Fi hotspot.
   - Set up internet connection with **Custom** settings and enable **Proxy Server**.
   - Enter your phone's IP address and Port `8080`.
   - Open **User's Guide** in console Settings to load the exploit.

### PS4 PKG Sender Mode
1. Connect your PS4 to the phone's Wi-Fi hotspot.
2. On PS4, open GoldHEN settings and enable **BinLoader Server** (Port 9090).
3. Switch to the **PS4 PKG Sender** tab in the app.
4. Select one or more `.pkg` files from your device storage.
5. Enter your PS4 IP address and tap **Send to PS4**.
6. The download will start automatically in the PS4 Notifications menu.

---

## Download

| Version | APK File | Description |
| :--- | :--- | :--- |
| **v1.2 (Latest)** | [**hotspot-webkit-ps4-ps5-v1.2.apk**](https://github.com/Fazzdevv/hotspot-webkit/releases/download/v1.2/hotspot-webkit-ps4-ps5-v1.2.apk) | Live Console Log, BGFT fix, optimized transfer buffer |
| **v1.1** | [hotspot-webkit-ps4-ps5-v1.1.apk](https://github.com/Fazzdevv/hotspot-webkit/releases/download/v1.1/hotspot-webkit-ps4-ps5-v1.1.apk) | Dual-tab interface, PKG Sender Port 9090, Web Ripper |
| **v1.0** | [hotspot-webkit-ps4-ps5-v1.0.apk](https://github.com/Fazzdevv/hotspot-webkit/releases/download/v1.0/hotspot-webkit-ps4-ps5-v1.0.apk) | Offline Exploit Portal, wildcard proxy |

Local APK files are also available in the repository under [`release/hotspot-webkit-ps4-ps5-v1.2.apk`](release/hotspot-webkit-ps4-ps5-v1.2.apk) and [`release/hotspot-webkit-ps4-ps5.apk`](release/hotspot-webkit-ps4-ps5.apk).

---

## License

This project is licensed under the [MIT License](LICENSE).
