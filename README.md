# ArAr Cicis (Asisten Keuangan Rumah Tangga & Dompet Cerdas)

Aplikasi Android modern berbasis **Kotlin** dan **Jetpack Compose (Material 3)** untuk mengelola keuangan rumah tangga, pos amplop belanja, tagihan rutin, serta dilengkapi asisten cerdas **"Si Cici"** bergaya *Jarvis* yang berjalan **100% Offline** di dalam perangkat ponsel.

---

## 🌟 Fitur Utama

### 1. Asisten Cerdas "Si Cici" (Offline SLM Jarvis)
- **100% On-Device Small Language Model (SLM):** Berjalan langsung di HP pengguna menggunakan model lokal terkuantisasi (rekomendasi utama **Qwen 2.5 1.5B Instruct** atau opsi Google **Gemma 2 2B IT**) tanpa memerlukan koneksi internet dan tanpa biaya server.
- **In-App Model Downloader:** Fitur unduh model langsung di dalam aplikasi dilengkapi pengecekan ruang penyimpanan, indikator progres *real-time* (%), kecepatan unduh (`KB/s`), tombol batal/hapus, serta dukungan mirror lokal.
- **Pemisahan Tugas (Bebas Halusinasi Aritmatika):**
  - **AI:** Hanya bertugas memahami bahasa percakapan pengguna dan mengubahnya menjadi skema JSON *Function Calling* (`ADD_EXPENSE`, `ADD_INCOME`, `ADD_WISHLIST`, `UPDATE_TRANSACTION`, `DELETE_TRANSACTION`, `PAY_BILL`, `CHECK_BALANCE`).
  - **Aplikasi Lokal:** Mengeksekusi mutasi database Room/SQLite dan melakukan kalkulasi matematika secara eksak tanpa risiko salah hitung.
- **Pemahaman Slang Pasar Tradisional:** Mengenali istilah slang belanja Indonesia (*"goceng"*, *"ceban"*, *"gocap"*, *"15rb"*, *"25 rebu"*, *"2.5jt"*).
- **Injeksi Konteks Dinamis:** Menyuntikkan saldo kas terkini dan 5 transaksi terakhir ke dalam *prompt*, memungkinkan perintah pembatalan atau ralat cepat (misal: *"Eh, rotinya kemahalan, gak jadi beli deh"*).
- **Mode Belanja Suara Hands-Free di Latar Belakang:**
  - Berjalan sebagai **Foreground Service** dengan tipe `microphone` sehingga pengguna dapat mencatat belanja via suara meski aplikasi diminimalkan atau membuka aplikasi lain.
  - **Privasi Terjamin:** Mikrofon diputus seketika saat aplikasi ditutup oleh pengguna (`onTaskRemoved` & `onDestroy`), mencegah mikrofon menyala di latar belakang.

---

### 2. Manajemen Pos Anggaran & Amplop (Budget Envelopes)
- Alokasi anggaran bulanan per kategori (Belanja Dapur, Kebutuhan Rumah Tangga, Jajan Anak, Listrik/Air/Gas, dll).
- Indikator progres sisa limit belanja dan peringatan dini *overbudget*.

---

### 3. Pencatatan Transaksi & Multi-Dompet
- Pencatatan transaksi pengeluaran dan pemasukan dengan multi-sumber dana: **Dompet Tunai**, **Rekening Bank**, dan **e-Wallet**.
- Dialog input transaksi cepat dilengkapi kalkulator matematika instan (misal ketik `15000+24000` langsung terhitung).

---

### 4. Daftar Belanja & Wishlist
- Catat kebutuhan belanja sebelum pergi ke pasar atau supermarket.
- Estimasi harga barang dan penandaan item yang sudah dibeli.

---

### 5. Tagihan Rutin & Hutang-Piutang
- Pelacak tagihan berkala (Listrik PLN, Air PDAM, WiFi Indihome, dll).
- Pencatatan hutang dan piutang keluarga dengan status pelunasan.

---

### 6. Keamanan, Privasi & Cadangan Data
- **100% Offline & Privat:** Seluruh data keuangan tersimpan aman di database SQLite lokal perangkat.
- **Backup & Restore SAF:** Ekspor dan impor data cadangan menggunakan *Storage Access Framework (SAF)* resmi Android tanpa meminta izin penyimpanan yang berbahaya.

---

## 🏗️ Arsitektur Aplikasi

- **Bahasa:** Kotlin (100%)
- **UI Toolkit:** Jetpack Compose (Material Design 3)
- **Arsitektur:** MVVM (Model-View-ViewModel) + Clean Architecture
- **Database Lokal:** Android Room SQLite Persistence dengan KSP
- **Asistensi Suara:** Android `SpeechRecognizer` + `TextToSpeech` API + Foreground Microphone Service
- **Build System:** Gradle 9.3.1 dengan Kotlin DSL

---

## 🛠️ Persyaratan Build & Instalasi

1. **Android Studio:** Versi Koala / Ladybug / Meerkat atau yang lebih baru.
2. **JDK:** Versi 17 (Temurin / OpenJDK).
3. **Android SDK:** Target SDK 35 (Minimum SDK: 24 / Android 7.0 Nougat).
4. **Build APK Rilis via CLI:**
   ```bash
   ./gradlew assembleRelease
   ```
   File APK rilis akan otomatis dikemas dengan format:
   `release-artifacts/ArAr Cicis v<versionName>.apk`
