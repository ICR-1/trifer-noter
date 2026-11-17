# Ringkasan Aset Visual — Trifer Noter

Seluruh aset A1 sampai A11 telah dibuat secara orisinal (kompatibel dengan lisensi **Apache-2.0**), divalidasi secara XML/SVG, dan disimpan di folder `design/out/`.

---

## 1. Daftar File yang Dihasilkan (`design/out/`)

### A1 — Palet Warna
- `palette.md` — Dokumentasi palet mode terang & gelap beserta alasan dan rasio kontras WCAG 2.1.
- `Color_snippet.kt` — Konstanta `Color(0xFF......)` Jetpack Compose siap tempel.

### A2 — Ikon Aplikasi (Adaptive, `108×108dp`, zona aman `66×66dp`)
- `ic_launcher_foreground.xml`
- `ic_launcher_background.xml`
- `ic_launcher.svg` *(pratinjau gabungan)*

### A3 — Ikon Monokrom / Themed Icon (`108×108dp`)
- `ic_launcher_monochrome.xml`
- `ic_launcher_monochrome.svg`

### A4 — Logo & Wordmark `"Trifer Noter"` (Path / Outline murni)
- `logo_horizontal_light.svg`
- `logo_horizontal_dark.svg`
- `logo_stacked_light.svg`
- `logo_stacked_dark.svg`

### A5 — Splash Icon (`288×288dp`, lingkaran aman `±2/3`)
- `ic_splash.xml`
- `ic_splash.svg`

### A6 — Ikon Notifikasi (`24×24dp`, `#FFFFFFFF` / transparan saja)
- `ic_stat_recording.xml`, `ic_stat_recording.svg`
- `ic_stat_transcribing.xml`, `ic_stat_transcribing.svg`
- `ic_stat_done.xml`, `ic_stat_done.svg`

### A7 — Ikon Aksi UI (`24×24dp`, gaya Material 2dp, `android:fillColor="#FF000000"`)
- `ic_rekam.xml`, `ic_rekam.svg`
- `ic_jeda.xml`, `ic_jeda.svg`
- `ic_stop.xml`, `ic_stop.svg`
- `ic_penanda.xml`, `ic_penanda.svg`
- `ic_salin.xml`, `ic_salin.svg`
- `ic_bagikan.xml`, `ic_bagikan.svg`
- `ic_simpan_file.xml`, `ic_simpan_file.svg`
- `ic_ekspor.xml`, `ic_ekspor.svg`
- `ic_cari.xml`, `ic_cari.svg`
- `ic_hapus.xml`, `ic_hapus.svg`
- `ic_putar.xml`, `ic_putar.svg`
- `ic_unduh_model.xml`, `ic_unduh_model.svg`
- `ic_kirim_ke_ai.xml`, `ic_kirim_ke_ai.svg`
- `ic_pengaturan.xml`, `ic_pengaturan.svg`
- `ic_edit.xml`, `ic_edit.svg`
- `ic_kembali.xml`, `ic_kembali.svg`

### A8 — Ilustrasi Onboarding (`320×240dp`, rasio 4:3, tanpa teks)
- `onboarding_1.xml`, `onboarding_1.svg` — Rekam di mana saja.
- `onboarding_2.xml`, `onboarding_2.svg` — Teks muncul *live* dan *offline* di HP.
- `onboarding_3.xml`, `onboarding_3.svg` — Kirim transkrip ke chat AI untuk dirangkum.
- `onboarding_4.xml`, `onboarding_4.svg` — Pasang model.

### A9 — Ilustrasi Empty State (`200×200dp`, ramah & tanpa teks)
- `empty_no_notes.xml`, `empty_no_notes.svg`
- `empty_no_results.xml`, `empty_no_results.svg`
- `empty_no_mic.xml`, `empty_no_mic.svg`
- `empty_no_model.xml`, `empty_no_model.svg`
- `empty_error.xml`, `empty_error.svg`

### A10 — Visual Tombol Rekam (`88×88dp`)
- `btn_record_idle.xml`, `btn_record_idle.svg`
- `btn_record_active.xml`, `btn_record_active.svg`
- `btn_record_paused.xml`, `btn_record_paused.svg`
- `record_button_notes.md` — Rekomendasi animasi (durasi, *easing*, *keyframe* denyut, dan haptik).

### A11 — Banner README (`1280×640`)
- `banner.svg`
- `banner.png`

---

## 2. Keputusan Desain Utama

1. **Identitas Visual & Palet (A1)**:
   - Memilih **Deep Teal** (`#0F6B6E` terang / `#52D1CA` gelap) sebagai warna utama yang tenang untuk penggunaan jangka panjang di kelas/rapat, dipadukan dengan **Slate Blue** (`#365573` / `#93B7DC`) untuk elemen pendukung/AI, serta **Coral-Red** (`#D13426` / `#FF6B5B`) sebagai aksen khusus status **"Sedang Merekam"**.
   - Semua pasangan teks/latar utama lolos **WCAG AA** (>= 4.5:1), dengan mayoritas pasangan teks mencapai **WCAG AAA** (>= 7.0:1).
2. **Konsep Ikon Aplikasi (A2, A3, A5)**:
   - Menggabungkan bentuk **halaman catatan bersudut lipat** dengan **kapsul mikrofon + gelombang suara** yang langsung mengalir menjadi **baris teks catatan** dan **titik live coral-red** — melambangkan suara yang langsung berubah menjadi catatan.
   - Seluruh elemen inti berada di kotak aman `[33..75] × [28..80]` pada kanvas `108×108dp` (nyaman di dalam zona aman `66×66dp` = `[21..87]`).
   - Pada `ic_launcher_monochrome.xml` (A3), seluruh detail dikonversi menjadi siluet hitam `#FF000000` dengan `android:fillType="evenOdd"`.
   - Pada `ic_splash.xml` (A5), ikon dibingkai medali bulat `r=68dp` (`136dp` diameter) di tengah `288×288dp` agar aman di dalam lingkaran `192dp` Android 12+ dan tetap kontras baik di tema terang maupun gelap.
3. **Wordmark Bebas Lisensi Font (A4 & A11)**:
   - Untuk memastikan 100% orisinal dan bebas ketergantungan font sistem berhak cipta, huruf `"Trifer Noter"` dibuat langsung sebagai **outline geometris rounded-sans (`<path>`)**, bukan `<text>` atau font eksternal.
4. **Ikon UI Siap-Tint (A7)**:
   - Semua 16 ikon aksi UI mengikuti grid `24×24dp` bergaya Material stroke `2dp` berujung membulat, yang diekspansi menjadi *filled outline path* menggunakan `android:fillColor="#FF000000"` sehingga kompatibel penuh dengan mekanisme *tint* Android (`ImageView app:tint` dan Compose `Icon(..., tint = ...)`).
   - Ikon `ic_simpan_file` memakai metafora disket simpan klasik, sedangkan `ic_ekspor` memakai nampan dengan panah keluar ke kanan-atas agar keduanya mudah dibedakan.
5. **Ilustrasi Tanpa Teks (A8 & A9)**:
   - Semua ilustrasi onboarding (`320×240dp`, 4:3) dan empty state (`200×200dp`) menggunakan bentuk sederhana, membulat, dan bebas teks (termasuk ikon `!` pada `empty_error` yang dibentuk dari kapsul + lingkaran).

---

## 3. Hasil Validasi

- **Validasi XML & SVG**: 35 file Android VectorDrawable `.xml` dan 39 file `.svg` lolos parsing `lxml.etree` tanpa error, memakai `<vector>` valid tanpa tag terlarang (`<text>`, `<style>`, `<filter>`, `<mask>`), seluruh gradient hanya di dalam `<aapt:attr>`, seluruh ikon A6 memakai `#FFFFFFFF`, dan seluruh ikon A7 + A3 memakai `#FF000000`.
- **Pemeriksaan Visual**: Seluruh SVG dan `banner.png` (`1280×640` px) telah dirender dan diperiksa secara visual.

---

## 4. Hal yang Perlu Dipertimbangkan / Direvisi Saat Integrasi

1. **Varian Mode Gelap untuk Ilustrasi A8 & A9**: Saat ini `onboarding_*.xml` dan `empty_*.xml` memakai warna latar lembut terang (`#D2F1F0`, `#DCE8F5`, `#FDE3E0`, `#FEF0D5`, `#FEE2E2`) yang tetap terbaca baik di atas kartu/layar gelap karena memiliki lingkaran latar belakang sendiri; namun jika ingin tampilan mode gelap lebih menyatu (tanpa lingkaran terang kontras), tim UI dapat menduplikasi ke `drawable-night/` dengan warna container gelap dari `palette.md` (`#0D4D50`, `#233C54`, `#691811`, `#543205`, `#6B1414`).
2. **Animasi Tombol Rekam (A10)**: `btn_record_active.xml` menyertakan dua cincin denyut statis untuk pratinjau/fallback (*Reduce Motion*). Untuk denyut animasi kontinu yang halus di Compose, disarankan menggambar dua cincin luar di belakang lingkaran inti `64dp` sesuai spesifikasi di `record_button_notes.md`.
3. **Konvensi Penamaan Logo A4**: `*_light.svg` berarti logo untuk **tema terang** (teks gelap di atas latar terang), dan `*_dark.svg` berarti logo untuk **tema gelap** (teks terang di atas latar gelap).
