# Hotspot WebKit PS4-PS5

[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B%20%28Android%208.0%2B%29-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20%28Android%2014%29-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Non-Root](https://img.shields.io/badge/Permissions-Non--Root%20Compatible-orange.svg)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Hotspot WebKit PS4-PS5** adalah aplikasi Android mandiri (*autonomous edge server*) yang mengubah smartphone Android Anda menjadi **Localhost Exploit Host & Web Server** berkecepatan tinggi tanpa memerlukan akses **Root**.

Aplikasi ini dirancang khusus untuk skenario **Offline Exploit Hosting PS4 & PS5**, memuat payload/jailbreak lokal melalui menu **Panduan Pengguna (User's Guide)** atau **Browser Internet** langsung dari hotspot portabel Android.

---

## Fitur Utama

- **Dynamic Web Ripper / Auto-Cache**: Kloning dan unduh website exploit online langsung dari URL tanpa perlu membuat file `.zip` manual. Aset HTML, CSS, JS, dan gambar diunduh secara paralel dan otomatis disajikan secara offline.
- **PS4 PKG Installer & Sender (Port 9090 GoldHEN)**: Terinspirasi langsung dari `PS4PkgSender.exe` v1.2.0 Desktop yang matang. Mendukung pengiriman fPKG ke PS4 via GoldHEN BinLoader (Port 9090), PlayGo JSON Manifest generator (`/json/{id}.json`), Deterministic ID SHA-256 (`PkgIdentifier`), Smart Fallback Matching anti-404 saat pause/resume unduhan, dan buffer streaming 256 KB (`206 Partial Content`).
- **Mode Host on Server (Resume Unduhan)**: Menyajikan berkas PKG langsung di server port 8080 untuk menangani error `0x80990086` atau melanjutkan unduhan yang sedang dijeda di menu Notifikasi PS4.
- **Multilingual Support (Bilingual ID 🇮🇩 / EN 🇬🇧)**: Pengalih bahasa instan di bagian header aplikasi yang tersimpan secara persisten tanpa perlu mengubah bahasa sistem perangkat Android.
- **All-Domain Direct Serving Engine (Wildcard 200 OK)**: Semua permintaan HTTP dari konsol (baik `manuals.playstation.net`, browser web, maupun URL lainnya) langsung disajikan dengan **`HTTP 200 OK`** dari file lokal `.zip` atau hasil web ripper tanpa redirect 302, mencegah error sandbox domain dan loop redirect.
- **Dukungan Penuh PS4 & PS5 User's Guide**: Memetakan path bahasa (`/document/{lang}/ps4/...` dan `/document/{lang}/ps5/...`) secara cerdas ke root berkas lokal Anda.
- **Tes Koneksi PS4 & PS5 Sukses (`netcheck.playstation.net`)**: Merespons request netcheck PlayStation dengan `200 OK` sehingga tes koneksi internet saat setup jaringan di konsol selalu berstatus **Sukses**.
- **Proteksi Update Sistem Otomatis (`update.playstation.net`)**: Memblokir pengecekan firmware update PlayStation secara aman agar konsol terhindar dari pembaruan sistem yang tidak diinginkan.
- **Dynamic ZIP Package Host**: Dapat menjalankan website atau paket exploit apa pun cukup dengan mengimpor file `.zip` dari penyimpanan internal HP menggunakan *Android Storage Access Framework (SAF)* dengan proteksi terhadap *Zip-Slip Vulnerability*.
- **Deteksi IP Native Hardware**: Secara otomatis mengiterasi interface jaringan hardware Android (`ap0`, `wlan1`, `swlan0`, `softap`) untuk mendeteksi alamat IPv4 tethering yang sebenarnya secara akurat.
- **HTTP 206 Partial Content (High-Throughput Streaming)**: Mendukung pemutaran media dan pengaliran file PKG puluhan GB dengan buffer 256 KB dan penanganan range byte (RFC 7233).
- **Modern Jetpack Compose UI**: Antarmuka bertema gelap (*Cyber Dark Slate*) dengan indikator status beranimasi pulsasi, generator **QR Code instan**, tombol satu ketukan untuk salin IP/URL, tabs sumber website, monitoring transfer kecepatan tinggi, serta **Live Traffic Radar**.
- **Background Persistence (Foreground Service & WakeLock)**: Dilengkapi Android Foreground Service dengan `PowerManager.PARTIAL_WAKE_LOCK` dan `WifiManager.WifiLock` agar server tetap aktif melayani konsol 24/7 meskipun layar HP dimatikan.

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
│  │     - Wildcard Direct Serving Engine (HTTP 200 OK)               │  │
│  │     - PlayStation Netcheck Responder (200 OK)                    │  │
│  │     - PlayStation Update Blocker (200 OK Empty)                  │  │
│  │     - Smart Path Resolver (PS4/PS5 User's Guide Normalizer)      │  │
│  │                                                                  │  │
│  │  3. Zip Package Manager:                                         │  │
│  │     - SAF Uri Importer + Zip-Slip Guard Extraction               │  │
│  │     - Auto-Mount to Document Root                                │  │
│  │                                                                  │  │
│  │  4. Realtime Traffic Radar:                                      │  │
│  │     - Request telemetry & live console monitoring                │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Wi-Fi Hotspot (Proxy: Port 8080)
        ┌───────────────────────────┴───────────────────────────┐
        ▼                                                       ▼
   [ Konsol PS4 ]                                          [ Konsol PS5 ]
   Proxy: IP_HP:8080                                       Proxy: IP_HP:8080
   -> Panduan Pengguna (Direct 200 OK)                     -> Panduan Pengguna (Direct 200 OK)
   -> Browser Internet (Direct 200 OK)                     -> Browser Internet (Direct 200 OK)
```

---

## Panduan Penggunaan

### 1. Di HP Android Server:
1. Nyalakan **Hotspot Portabel / Tethering** di pengaturan Android Anda.
2. Buka aplikasi **Hotspot WebKit PS4-PS5**.
3. Periksa bagian **Hardware Network Interface**: aplikasi akan otomatis mendeteksi **IP Asli** (misal `192.168.43.1`) dan **Port 8080**.
4. Tekan tombol **"Import .ZIP Baru"** untuk memilih website / paket exploit kustom Anda dari storage HP.
5. Tekan tombol **"Nyalakan Server & Proxy (Port 8080)"**.

### 2. Di Konsol PS4 atau PS5:
1. Hubungkan konsol ke Hotspot Wi-Fi Android ini.
2. Buka **Pengaturan (Settings)** > **Jaringan (Network)** > **Siapkan Koneksi Internet (Set Up Internet Connection)**.
3. Pilih **Gunakan Wi-Fi** > Pilih mode **Khusus (Custom / Advanced Settings)**.
4. Pada bagian **Server Proksi (Proxy Server)**:
   - Pilih **Gunakan (Use)**
   - **Alamat (Address/Host)**: Masukkan **IP Native** dari aplikasi (misal `192.168.43.1`)
   - **Port**: `8080`
5. Pengaturan lainnya (IP, DNS, MTU) biarkan **Otomatis (Automatic)**.
6. **Membuka Website / Exploit**:
   - Di PS4: Buka menu **Pengaturan** > **Panduan Pengguna / Informasi Bermanfaat** > **Panduan Pengguna (User's Guide)**.
   - Di PS5: Buka menu **Pengaturan** > **Panduan & Kiat, Kesehatan & Keselamatan, dan Informasi Lainnya** > **Panduan Pengguna (User's Guide)**.
   - *(Atau buka Browser Internet dan ketik URL apa saja)*.
7. **Hasil**: Halaman HTML dan seluruh aset dari file `.ZIP` Anda akan langsung terbuka secara instan (*Direct 200 OK*) tanpa error domain ataupun loop redirect!

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
File APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk` atau folder `release/hotspot-webkit-ps4-ps5-v1.0.apk`.

---

## Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE). Bebas digunakan, dimodifikasi, dan didistribusikan untuk keperluan personal maupun komersial.
