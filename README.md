# Hotspot WebKit v0.1

[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B%20%28Android%208.0%2B%29-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20%28Android%2014%29-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Non-Root](https://img.shields.io/badge/Permissions-Non--Root%20Compatible-orange.svg)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Hotspot WebKit** adalah aplikasi Android mandiri (*autonomous edge server*) yang mengubah smartphone Android Anda menjadi **Localhost Web Server & Proxy Interceptor** berkecepatan tinggi tanpa memerlukan akses **Root**. 

Aplikasi ini dirancang khusus untuk skenario **Offline Intranet**, sistem informasi darurat, portal kampus/sekolah lokal, ujian offline, ataupun *captive landing page* yang dijalankan langsung dari hotspot portabel Android.

---

## Fitur Utama

- **Deteksi IP Native Hardware (Bukan Tebakan)**: Secara otomatis mengiterasi interface jaringan hardware Android (`ap0`, `wlan1`, `swlan0`, `softap`) untuk mendeteksi alamat IPv4 tethering yang sebenarnya secara akurat.
- **Dynamic ZIP Package Host**: Dapat menjalankan website apa pun (HTML, CSS, JS, React/Vue build) cukup dengan mengimpor file `.zip` dari penyimpanan internal HP menggunakan *Android Storage Access Framework (SAF)*. Dilengkapi proteksi keamanan terhadap *Zip-Slip Vulnerability*.
- **Proxy Interceptor (HTTP 302 Redirect)**: Menjalankan proxy lokal di Port `8080`. Saat klien membuka website apa pun di browser (misal: `google.com`, `detik.com`), lalu lintas langsung dibelokkan secara otomatis (*HTTP 302 Found*) ke website lokal dari file ZIP Anda.
- **HTTP 206 Partial Content (Media Streaming)**: Mendukung pemutaran audio dan video dengan fitur *seeking* / *scrubbing* yang mulus langsung di browser klien.
- **Modern Jetpack Compose UI**: Antarmuka bertema gelap (*Cyber Dark Slate*) yang elegan dengan indikator status beranimasi pulsasi, generator **QR Code instan**, tombol satu ketukan untuk salin IP/URL, serta **Live Traffic Radar** untuk memantau request klien secara real-time.
- **Background Persistence (Foreground Service & WakeLock)**: Dilengkapi Android Foreground Service dengan `PowerManager.PARTIAL_WAKE_LOCK` dan `WifiManager.WifiLock` agar server tetap aktif melayani klien 24/7 meskipun layar HP dimatikan.
- **WPAD / PAC Support**: Menyediakan endpoint script auto-config proxy di `/wpad.dat` dan `/proxy.pac`.

---

## Arsitektur Sistem

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID APPLICATION                             │
│                                                                        │
│  ┌───────────────────────┐   ┌──────────────────────────────────────┐  │
│  │   Jetpack Compose     │   │     Foreground Edge Service          │  │
│  │  Interactive Dashboard│◄──┤  (WakeLock, Lifecycle, Notification) │  │
│  └───────────────────────┘   └──────────────────┬───────────────────┘  │
│                                                 │                      │
│  ┌──────────────────────────────────────────────┴───────────────────┐  │
│  │                     CORE ENGINE SUBSYSTEMS                       │  │
│  │                                                                  │  │
│  │  1. Hardware Network Utils:                                      │  │
│  │     - Native Interface Resolver (ap0, wlan1, swlan0)             │  │
│  │                                                                  │  │
│  │  2. Local Edge Server (Port 8080):                               │  │
│  │     - Static Web Server + SPA Fallback                           │  │
│  │     - HTTP 206 Range Streaming (Video/Audio)                     │  │
│  │     - Interceptor: Catch external host -> 302 Redirect to Portal │  │
│  │                                                                  │  │
│  │  3. Zip Package Manager:                                         │  │
│  │     - SAF Uri Importer + Zip-Slip Guard Extraction               │  │
│  │     - Auto-Mount to Document Root                                │  │
│  │                                                                  │  │
│  │  4. Realtime Traffic Radar:                                      │  │
│  │     - Request telemetry & live client monitoring                 │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Wi-Fi Hotspot (Port 8080)
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
  [ Klien Android ]           [ Klien iPhone ]           [ Klien Laptop ]
  Proxy: IP:8080              Proxy: IP:8080             Proxy: IP:8080
  -> Redirect ke Portal       -> Redirect ke Portal      -> Redirect ke Portal
```

---

## Panduan Penggunaan Cepat

### 1. Di HP Android Server:
1. Nyalakan **Hotspot Portabel / Tethering** di pengaturan Android Anda.
2. Buka aplikasi **Hotspot WebKit**.
3. Periksa bagian **Hardware Network Interface**: aplikasi akan otomatis mendeteksi **IP Asli** (misal `192.168.43.1`) dan **Port 8080**.
4. Tekan tombol **"Nyalakan Server & Proxy (Port 8080)"**.
5. *(Opsional)* Tekan **"Import .ZIP Baru"** untuk memilih website kustom Anda dari storage HP.

### 2. Di Perangkat Klien (HP / Laptop Lain):
1. Hubungkan perangkat klien ke Wi-Fi Hotspot Android tersebut.
2. Buka pengaturan Wi-Fi pada klien, klik **Pengaturan Lanjutan (Advanced)**:
   - **Proxy**: Pilih `Manual`
   - **Proxy Hostname**: Masukkan alamat **IP Native** yang tampil di aplikasi
   - **Proxy Port**: `8080`
   - **Bypass**: Kosongkan
3. Buka browser di klien, ketik alamat web apa saja (misal `google.com` atau `tes.com`).
4. **Hasil**: Browser klien akan otomatis dibelokkan (*redirect*) langsung ke website lokal dari file ZIP Anda!

---

## Cara Build dari Source Code

### Prasyarat:
- Java JDK 17
- Android SDK 34 (Build-Tools 34.0.0)

### Langkah Kompilasi:
```bash
# Clone repository
git clone https://github.com/Fazzdevv/hotspot-webkit.git
cd hotspot-webkit

# Build APK Debug
./gradlew assembleDebug
```
File APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk`.

---

## Struktur Direktori Proyek

```
hotspot-webkit/
├── app/
│   ├── src/main/
│   │   ├── java/com/fazzdev/offlineedgeportal/
│   │   │   ├── core/
│   │   │   │   ├── NetworkUtils.kt          # Resolusi hardware IP native
│   │   │   │   ├── LocalEdgeServer.kt       # Web server & 302 Proxy interceptor
│   │   │   │   └── ZipPackageManager.kt     # Handler ekstraksi & mount ZIP aman
│   │   │   ├── service/
│   │   │   │   └── EdgeServerService.kt     # Foreground Service & WakeLock
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt          # Activity & SAF Picker launcher
│   │   │       ├── MainViewModel.kt         # Reactive state & logic
│   │   │       ├── screens/DashboardScreen.kt # Compose UI Dashboard
│   │   │       └── util/QRCodeGenerator.kt  # Generator QR Code
│   │   ├── res/                             # Resource layout, strings, icons
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE). Bebas digunakan, dimodifikasi, dan didistribusikan untuk keperluan personal maupun komersial.
