# Hotspot WebKit PS4-PS5

[![Latest Release](https://img.shields.io/badge/Release-v1.1-blue.svg)](https://github.com/Fazzdevv/hotspot-webkit/releases/tag/v1.1)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B%20%28Android%208.0%2B%29-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20%28Android%2014%29-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Non-Root](https://img.shields.io/badge/Permissions-Non--Root%20Compatible-orange.svg)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Hotspot WebKit PS4-PS5** adalah aplikasi Android mandiri (*autonomous edge server*) yang mengubah smartphone Android Anda menjadi **Localhost Exploit Host & PS4 PKG Sender** berkecepatan tinggi tanpa memerlukan akses **Root**.

Aplikasi ini dirancang khusus untuk komunitas PlayStation (PS4 & PS5) dengan dua mode utama:
1. **Offline Exploit Hosting**: Memuat payload/jailbreak lokal melalui menu **Panduan Pengguna (User's Guide)** atau **Browser Internet** langsung dari hotspot portabel Android.
2. **PS4 PKG Sender (Port 9090 GoldHEN)**: Mengirim dan menginstal berkas game/update `.pkg` ke PS4 secara nirkabel melalui injeksi payload BinLoader Port 9090 atau hosting server lokal untuk melanjutkan unduhan yang dijeda.

---

## 🚀 Fitur Utama di v1.1

### 1. Dual-Tab Navigation & Server Saling Eksklusif (Mutually Exclusive)
- Layar dashboard dibagi menjadi dua tab terisolasi: **`[ 🌐 WebKit Exploit ]`** dan **`[ 🎮 PS4 PKG Sender ]`**.
- **Server Safety Isolation**: Mengaktifkan server WebKit otomatis mematikan mode server PKG, dan sebaliknya. Keduanya berbagi port 8080 secara dinamis tanpa tabrakan port (`bind address in use`).

### 2. PS4 PKG Installer & Sender (GoldHEN Port 9090)
- **GoldHEN BinLoader Integration**: Injeksi payload 16 KB resmi via socket port 9090 dengan patching Little-Endian otomatis.
- **PlayGo JSON Manifest Generator (`/json/{id}.json`)**: Menghasilkan manifest potongan paket yang kompatibel 100% dengan download manager PlayStation 4.
- **Deterministic ID SHA-256 (`PkgIdentifier`)**: Identifikasi berkas PKG berbasis hash Sony package digest (`0xFE0`), Title ID, Content ID, dan ukuran byte.
- **Smart 8-Layer Fallback Matching**: Mencegah HTTP 404 saat pause/resume unduhan di menu Notifikasi PS4.
- **Buffer Streaming 256 KB (`206 Partial Content`)**: Pengaliran file raksasa (hingga puluhan GB) dengan latensi ultra-rendah dan throughput tinggi melalui Android SAF (`ParcelFileDescriptor`).
- **UI Minimalis & Modern**: Tombol aksi seimbang `[ ➢ Kirim ke PS4 ]` dan `[ ☁ Taruh di Server ]` (untuk melanjutkan unduhan atau mengatasi error `0x80990086`).

### 3. Dynamic Web Ripper / Auto-Cache (Clone Web Exploit)
- Kloning dan unduh website exploit online langsung dari URL (misal Karo Exploit, KME PS4) tanpa perlu membuat file `.zip` manual.
- Seluruh aset HTML, CSS, JS, dan gambar diunduh paralel dan otomatis disajikan secara offline dari memori internal HP.

### 4. Multilingual Support (Bilingual ID 🇮🇩 / EN 🇬🇧)
- Pengalih bahasa instan di header aplikasi antara Bahasa Indonesia dan Bahasa Inggris dengan penyimpanan preferensi persisten.

### 5. All-Domain Direct Serving Engine (Wildcard 200 OK)
- Semua permintaan HTTP dari konsol (baik `manuals.playstation.net`, browser web, maupun domain publik) langsung disajikan dengan **`HTTP 200 OK`** tanpa redirect 302, mencegah error domain sandbox pada browser konsol.

### 6. Proteksi Jaringan PlayStation
- **Tes Koneksi PS4 & PS5 Sukses (`netcheck.playstation.net`)**: Tes koneksi internet saat konfigurasi Wi-Fi selalu sukses.
- **Proteksi Update Sistem Otomatis (`update.playstation.net`)**: Memblokir pengecekan firmware update PlayStation secara aman agar konsol terhindar dari pembaruan firmware yang tidak diinginkan.

### 7. Modern Jetpack Compose UI & Background Persistence
- Antarmuka bertema gelap (*Cyber Dark Slate*) dengan monitor kecepatan transfer live (MB/s), counter byte terkirim, generator QR code, serta **Live Traffic Radar**.
- Berjalan stabil di latar belakang (*Foreground Service*) dengan `PowerManager.PARTIAL_WAKE_LOCK` dan `WifiManager.WifiLock` sehingga server tetap aktif 24/7 meskipun layar HP dimatikan.

---

## 🛠 Panduan Penggunaan

### A. Menggunakan WebKit Exploit Server (Tab 1)
1. Nyalakan **Hotspot Portabel** di pengaturan Android.
2. Buka aplikasi **Hotspot WebKit PS4-PS5** di tab **`[ 🌐 WebKit Exploit ]`**.
3. Import file `.zip` exploit Anda atau gunakan tab **Web Ripper** untuk mengkloning URL exploit online.
4. Tekan **"Nyalakan Server WebKit"**.
5. Pada konsol PS4/PS5:
   - Hubungkan ke Wi-Fi Hotspot HP.
   - Atur koneksi internet > mode **Khusus (Custom)** > Proxy: **Gunakan (Use)**.
   - Masukkan IP HP (misal `192.168.43.1`) dan Port `8080`.
   - Buka **Panduan Pengguna (User's Guide)** di Pengaturan konsol untuk memuat exploit!

### B. Mengirim Berkas .PKG ke PS4 (Tab 2)
1. Sambungkan PS4 ke Hotspot Wi-Fi HP ini.
2. Pada PS4 yang sudah terpasang GoldHEN: buka menu **GoldHEN** > centang/aktifkan **"BinLoader Server"** (Port 9090).
3. Buka tab **`[ 🎮 PS4 PKG Sender ]`** di aplikasi HP.
4. Tekan **"Pilih Berkas .PKG"** untuk memilih berkas game/update dari penyimpanan HP.
5. Masukkan **Alamat IP PS4** Anda pada kolom input (Port otomatis 9090).
6. Tekan **"Kirim ke PS4"**:
   - Aplikasi akan menginjeksi payload 16KB ke port 9090 GoldHEN dan mengirimkan metadata paket PlayGo.
   - Notifikasi unduhan akan langsung muncul di pojok kiri atas layar PS4!
7. *Jika muncul error `0x80990086`*: artinya paket sudah ada di antrean konsol. Tekan **"Taruh di Server"**, lalu buka menu Notifikasi di PS4 dan pilih **Lanjutkan (Resume)**.

---

## 📦 Download APK Release

| Versi | Berkas APK | Keterangan |
| :--- | :--- | :--- |
| **v1.1 (Terbaru)** | [**hotspot-webkit-ps4-ps5-v1.1.apk**](https://github.com/Fazzdevv/hotspot-webkit/releases/download/v1.1/hotspot-webkit-ps4-ps5-v1.1.apk) | Dual-Tab UI, PKG Sender GoldHEN Port 9090, Web Ripper, Mutually Exclusive Servers |
| **v1.0** | [hotspot-webkit-ps4-ps5-v1.0.apk](https://github.com/Fazzdevv/hotspot-webkit/releases/download/v1.0/hotspot-webkit-ps4-ps5-v1.0.apk) | All-Domain Direct Serving Engine, Wildcard Proxy, Offline Exploit Portal |

Berkas APK lokal juga tersedia langsung di repositori pada folder [`release/hotspot-webkit-ps4-ps5-v1.1.apk`](release/hotspot-webkit-ps4-ps5-v1.1.apk) dan [`release/hotspot-webkit-ps4-ps5.apk`](release/hotspot-webkit-ps4-ps5.apk).

---

## 🏗 Cara Build dari Source Code

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
Berkas APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk` atau `release/hotspot-webkit-ps4-ps5-v1.1.apk`.

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE). Bebas digunakan, dimodifikasi, dan didistribusikan untuk keperluan personal maupun komersial.

