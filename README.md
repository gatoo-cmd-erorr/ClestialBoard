# ClestialKeyboard

Aplikasi keyboard Android (Kotlin) bergaya iOS (terang/gelap) dengan aplikasi pendamping bergaya LEGO hitam-putih chrome berkilau.
Dilengkapi **Kalkulator Umum & Ilmiah**, **11 Modul Matematika SMP Kelas 9 Eksak + Kartu Rumus**, **Keyboard & Transliterator Aksara Jawa Dua Arah**, serta integrasi **Tanya AI via Proxy 9router**, di samping fitur papan klip, kontrol kursor, resize, emoji, saran kata, dan terjemahan di perangkat.

- Package: `com.clestial.keyboard`
- minSdk 26 (Android 8.0), targetSdk 34
- Unit test & build APK otomatis lewat GitHub Actions (`.github/workflows/build.yml`)

## Fitur Utama

1. **Keyboard iOS Light & Dark + Tools Bar:**
   - Papan klip teks & screenshot, kontrol kursor, ubah ukuran tinggi/posisi, serta terjemahan di perangkat (ML Kit).
   - **Kalkulator Ilmiah di Keyboard (`calcOpen`):** `√`, `∛`, `ⁿ√`, `x²`, `x³`, `^`, `1/x`, `abs`, `sin/cos/tan`, `log/ln`, `π`, `DEG/RAD`, dan tombol **`SMP 9`**.
   - **Aksara Jawa Dua Arah (`javaOpen`):** Mode `Jawa → Latin` (dengan papan tombol 20 Carakan, Sandhangan, Pasangan otomatis, Swara, Pada, dan Angka Jawa) serta mode `Latin → Jawa`.
   - **Tanya AI 9router (`aiOpen`):** Tanya jawab langsung di bilah atas keyboard lewat server proxy tipis tanpa menyimpan API key di aplikasi Android.
2. **Kalkulator Matematika SMP Kelas 9 (11 Modul Eksak):**
   - **Aljabar:** SPLDV, Bilangan Berpangkat & Bentuk Akar, Persamaan Kuadrat, Fungsi Kuadrat, Persamaan Garis Lurus & Fungsi Linear (plus sketsa garis SVG/Canvas), PLSV & Pertidaksamaan Linear Satu Variabel (dengan garis bilangan & sifat pembalikan tanda).
   - **Geometri:** Transformasi Geometri, Kekongruenan & Kesebangunan, Bangun Ruang Sisi Lengkung (π eksak, 22/7, 3,14).
   - **Statistika & Peluang:** Statistika (mean eksak, median, modus, jangkauan, Q1–Q3, IQR) dan Peluang & Frekuensi Harapan.
   - Dilengkapi **Kartu Rumus** per modul dan parsing **Soal Cerita AI** tervalidasi.
3. **Auto-Jawab Soal ABCD (Pilihan Ganda) Real-Time (Bukan Mock / Visual Dummy):**
   - **Accessibility Service Auto-Click (`AutoAnswerAccessibilityService`):** Mengenali komponen soal dan opsi A, B, C, D di layar aplikasi ujian/kuis secara dinamis dan melakukan klik fisik/aksi otomatis (`ACTION_CLICK` & `dispatchGesture`) pada opsi jawaban yang benar.
   - **Engine Pemecah Cerdas (`AbcdSolver`):** Memanfaatkan AI (ChatGPT API sk-... / Proxy 9router) serta dilengkapi Solver Heuristik & Matematika Eksak lokal mandiri (offline).
   - **Menu Pop-up Hide Anti-Detek:** Pengaturan lengkap di pop-up mengambang tersamar (Tab 🎯 Auto ABCD) meliputi switch aktif, mode Auto-Klik vs Kunci Saja, jeda waktu (0s, 1.5s, 2s, 3s anti-detek), status izin, dan tombol pemicu pemindaian langsung.
   - **Selection Box / OCR Crop:** Mengarahkan kotak seleksi neon biru ke soal di aplikasi apa pun, langsung mendeteksi ABCD, mengekstrak kunci, dan auto-click.

## Mengambil APK dari GitHub Actions

1. Buka tab **Actions** di repositori GitHub. Workflow menjalankan `gradle testDebugUnitTest assembleDebug` secara otomatis setiap kali ada `push`.
2. Tunggu hingga build hijau, lalu unduh artifact **`ClestialKeyboard-debug-apk`**.
3. Ekstrak file `.zip` dan pasang `app-debug.apk` di perangkat Android.

## Menjalankan Proxy AI 9router

Lihat petunjuk lengkap di [proxy/README.md](proxy/README.md). API key hanya disimpan pada environment variable server proxy (`NINE_ROUTER_API_KEY`), bukan di aplikasi Android.

## Mengaktifkan keyboard

Buka aplikasi ClestialKeyboard → Beranda → ikuti dua langkah (aktifkan, lalu pilih sebagai keyboard utama).

## Struktur singkat

- `app/src/main/java/com/clestial/keyboard/` — kode Kotlin
  - Aplikasi & UI: `MainActivity`, `MathModulesActivity`, `SettingsActivity`, `PreferencesActivity`, `Prefs`, `ClestialApp`, `SystemBars`, `LegoVisualViews`
  - Keyboard & Aksara Jawa: `ClestialIME`, `ClestialKeyboardView`, `KeyboardLayouts`, `JavaneseScript`
  - Kalkulator & 11 Modul Matematika SMP 9: `math/ExactMath`, `math/MathModule`, `math/MathRegistry`, `math/FormulaCards`, `math/ScientificCalculator`, `math/LineSketchView`, `math/AiRouterClient`, serta ke-11 file modul
- `app/src/test/java/com/clestial/keyboard/` — unit test JUnit (`MathModulesTest`, `JavaneseAndCalcTest`)
- `proxy/` — server proxy tipis Node.js 9router (`server.mjs`, `test_proxy.mjs`, `README.md`)
- `.github/workflows/build.yml` — unit test & build APK otomatis
