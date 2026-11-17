# BRIEF ASET — Trifer Noter

Kamu adalah **asset maker** untuk aplikasi Android bernama **Trifer Noter**. Tugasmu hanya membuat aset visual. **Jangan menyentuh kode (Kotlin, Gradle, manifest) dan jangan menulis di luar folder `design/`.** Semua hasil ditulis ke `design/out/`.

## Tentang aplikasi

Trifer Noter merekam suara (rapat, kuliah, presentasi) dan mengubahnya menjadi teks **secara live, offline di HP**, lalu pengguna bisa mengirim transkrip ke chat AI untuk dirangkum. Bahasa antarmuka: **Bahasa Indonesia**. Gratis dan open source, jadi semua aset harus **orisinal** dan boleh dirilis di bawah lisensi Apache-2.0 (jangan meniru logo merek lain, jangan memakai aset berhak cipta pihak lain, font hanya yang berlisensi open source seperti OFL).

Nama "Trifer Noter" tidak punya arti baku. Kamu bebas memilih konsep, tapi harus terasa: **tenang, tepercaya, modern, cepat dipahami**. Dipakai sambil duduk di kelas/rapat.

Pengguna: mahasiswa, pekerja kantoran, dosen. Fokus utama layar: tombol **Rekam** besar dan **teks live**.

## Aturan teknis umum

- Format utama: **Android VectorDrawable XML** (`<vector>`), bukan bitmap, sedapat mungkin. Hanya pakai atribut yang didukung Android (`path`, `group`, `clip-path`, warna hex, `android:pathData`). Tidak boleh: gradient kompleks di luar yang didukung (`<gradient>` di dalam `aapt:attr` boleh), filter, mask, teks, font, atau `style`.
- Selain XML, sediakan juga **SVG** dari tiap aset sebagai sumber.
- Nama file `snake_case`, huruf kecil, tanpa spasi.
- Gaya konsisten: bentuk sederhana, sudut membulat, garis bersih. Ikon UI mengikuti Material (grid 24dp, stroke 2dp, ujung membulat).
- Tidak ada teks di dalam ikon aplikasi.
- Cek kontras (WCAG AA) untuk pasangan warna teks/latar di palet.
- Dukung **mode terang dan gelap**.

## Daftar aset (kerjakan berurutan, A1 dulu)

| # | Aset | Spesifikasi | Output di `design/out/` |
|---|---|---|---|
| A1 | **Palet warna** | Terang + gelap. Warna utama (primary), sekunder, **aksen "sedang merekam"** (merah/oranye yang jelas), latar, permukaan, teks, dan warna status (sukses, gagal, proses). Beri nilai hex dan alasan singkat. Tampilkan juga rasio kontras teks/latar. | `palette.md` dan `Color_snippet.kt` (daftar `val ... = Color(0xFF......)` siap tempel) |
| A2 | **Ikon aplikasi (adaptive)** | Kanvas **108×108dp**. Konten penting harus di **zona aman 66×66dp** di tengah (sisanya bisa terpotong oleh mask). Layer *foreground* dan *background* **terpisah**. Konsep: mikrofon atau gelombang suara yang bergabung dengan halaman/catatan. | `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher.svg` (gabungan untuk pratinjau) |
| A3 | **Ikon monokrom (themed icon)** | Satu warna (hitam di atas transparan), siluet sederhana dari A2, 108×108dp, zona aman 66dp | `ic_launcher_monochrome.xml` |
| A4 | **Logo & wordmark "Trifer Noter"** | Versi horizontal dan tumpuk, masing-masing terang dan gelap. Teks sebagai **path** (outline), bukan font. | `logo_horizontal_light.svg`, `logo_horizontal_dark.svg`, `logo_stacked_light.svg`, `logo_stacked_dark.svg` |
| A5 | **Splash icon** | Android 12+ splash. Konten dalam lingkaran aman ±2/3 dari 288dp. | `ic_splash.xml` |
| A6 | **Ikon notifikasi** | 24dp, **putih/transparan saja** (aturan Android). Tiga ikon: merekam, mentranskrip, selesai. | `ic_stat_recording.xml`, `ic_stat_transcribing.xml`, `ic_stat_done.xml` |
| A7 | **Ikon aksi UI** | 24dp, stroke 2dp, gaya Material. Daftar: rekam, jeda, stop, penanda (bookmark), salin, bagikan, simpan_file, ekspor, cari, hapus, putar, unduh_model, kirim_ke_ai, pengaturan, edit, kembali. Pakai `android:fillColor="#FF000000"` agar bisa diwarnai (tint). | `ic_<nama>.xml` untuk tiap ikon |
| A8 | **Ilustrasi onboarding** | 4 slide, vektor, memakai palet A1: (1) rekam di mana saja, (2) teks muncul live dan offline di HP, (3) kirim transkrip ke chat AI untuk dirangkum, (4) pasang model. Rasio sekitar 4:3, tanpa teks. | `onboarding_1.xml` … `onboarding_4.xml` + SVG |
| A9 | **Ilustrasi empty state** | Belum ada catatan, hasil cari kosong, izin mikrofon belum diberikan, model belum terpasang, gagal memproses. Ramah, sederhana, tanpa teks. | `empty_no_notes.xml`, `empty_no_results.xml`, `empty_no_mic.xml`, `empty_no_model.xml`, `empty_error.xml` + SVG |
| A10 | **Visual tombol Rekam** | Tombol besar (±88dp) dengan tiga status: siap, sedang merekam (aksen merah/oranye, ada ring/denyut), dijeda. | `btn_record_idle.xml`, `btn_record_active.xml`, `btn_record_paused.xml` + `record_button_notes.md` (saran animasi: durasi dan easing) |
| A11 | **Banner README** | 1280×640 PNG/SVG, pakai logo A4 | `banner.svg`, `banner.png` |

*(Aset Play Store sengaja tidak diminta sekarang.)*

## Cara kerja

1. Kerjakan A1 dulu, lalu pakai palet itu untuk semua aset berikutnya.
2. Setelah selesai tiap aset, **validasi** bahwa XML valid (mis. parsing XML). Perbaiki jika error.
3. Tulis ringkasan akhir di `design/out/RINGKASAN.md`: daftar file, keputusan desain, dan hal yang perlu direvisi.
4. Jika ada keraguan, pilih opsi yang paling sederhana dan catat di `RINGKASAN.md`, jangan berhenti untuk bertanya.
