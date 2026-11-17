# Palet Warna — Trifer Noter (A1)

Konsep visual: **"Tenang, Terpercaya, Fokus Catatan"**.  
Palet menggunakan **Deep Teal** sebagai warna utama yang tenang untuk sesi kuliah/rapat berdurasi lama, **Slate Blue** sebagai warna sekunder untuk elemen pendukung dan AI/organisasi, serta **Coral Red** sebagai aksen khusus **"Sedang Merekam"** agar status rekaman langsung terlihat tanpa mengganggu kenyamanan baca.

---

## 1. Mode Terang (Light Mode)

| Peran Token | Nama Kotlin | Hex | Alasan Pemilihan |
|---|---|---|---|
| **Primary** | `TriferPrimaryLight` | `#0F6B6E` | Teal dalam yang tenang, profesional, tidak melelahkan mata di kelas/rapat. |
| **On Primary** | `TriferOnPrimaryLight` | `#FFFFFF` | Teks/ikon putih bersih di atas tombol dan elemen utama. |
| **Primary Container** | `TriferPrimaryContainerLight` | `#D2F1F0` | Latar lembut untuk chip aktif, sorotan transkrip, dan kartu penekanan. |
| **On Primary Container** | `TriferOnPrimaryContainerLight` | `#063739` | Teks teal sangat gelap agar kontras tetap tinggi di atas container. |
| **Secondary** | `TriferSecondaryLight` | `#365573` | Biru slate netral untuk aksi sekunder (bagikan, kirim ke AI, penanda). |
| **On Secondary** | `TriferOnSecondaryLight` | `#FFFFFF` | Teks/ikon putih di atas elemen sekunder. |
| **Secondary Container** | `TriferSecondaryContainerLight` | `#DCE8F5` | Latar lembut untuk kartu info atau aksi AI/ringkasan. |
| **On Secondary Container** | `TriferOnSecondaryContainerLight` | `#13293D` | Biru tua dengan keterbacaan tinggi. |
| **Aksen Sedang Merekam** | `TriferRecordingLight` | `#D13426` | Merah-oranye (vermilion) tegas dan jelas untuk tombol & indikator rekam aktif. |
| **On Recording** | `TriferOnRecordingLight` | `#FFFFFF` | Ikon/teks putih kontras tinggi di atas tombol rekam aktif. |
| **Recording Container** | `TriferRecordingContainerLight` | `#FDE3E0` | Latar lembut untuk badge/banner "Sedang merekam..." |
| **On Recording Container** | `TriferOnRecordingContainerLight` | `#6E140C` | Merah marun gelap untuk teks status rekaman. |
| **Background** | `TriferBackgroundLight` | `#F6F8F9` | Putih-abu kertas yang lembut (mengurangi silau layar dibanding `#FFFFFF` penuh). |
| **On Background** | `TriferOnBackgroundLight` | `#111A1E` | Hitam-slate lembut untuk teks utama agar nyaman dibaca lama. |
| **Surface** | `TriferSurfaceLight` | `#FFFFFF` | Permukaan kartu catatan dan area teks live yang bersih. |
| **On Surface** | `TriferOnSurfaceLight` | `#111A1E` | Teks utama di atas kartu/surface. |
| **Surface Variant** | `TriferSurfaceVariantLight` | `#E7EEF0` | Pemisah halus untuk panel kontrol, input pencarian, dan kartu sekunder. |
| **On Surface Variant** | `TriferOnSurfaceVariantLight` | `#44545A` | Teks sekunder (cap waktu, durasi, label metadata). |
| **Outline** | `TriferOutlineLight` | `#718389` | Garis tepi kartu, divider, dan tombol outlined. |
| **Status: Sukses** | `TriferSuccessLight` | `#16794B` | Hijau zamrud tenang untuk "Selesai / Tersimpan / Model siap". |
| **On Success** | `TriferOnSuccessLight` | `#FFFFFF` | Teks/ikon putih di atas badge sukses. |
| **Success Container** | `TriferSuccessContainerLight` | `#D7F3E3` | Latar notifikasi/chip status sukses. |
| **On Success Container** | `TriferOnSuccessContainerLight` | `#0A3D24` | Teks hijau gelap di atas container sukses. |
| **Status: Gagal** | `TriferErrorLight` | `#B91C1C` | Merah tua (lebih dingin dari aksen rekam) untuk error izin/proses gagal. |
| **On Error** | `TriferOnErrorLight` | `#FFFFFF` | Teks/ikon putih di atas tombol/badge gagal. |
| **Error Container** | `TriferErrorContainerLight` | `#FEE2E2` | Latar banner kesalahan. |
| **On Error Container** | `TriferOnErrorContainerLight` | `#660E0E` | Teks merah gelap di atas banner kesalahan. |
| **Status: Proses** | `TriferProcessingLight` | `#A15607` | Amber hangat untuk proses "Mentranskrip / Mengunduh model / Dijeda". |
| **On Processing** | `TriferOnProcessingLight` | `#FFFFFF` | Teks/ikon putih di atas badge proses. |
| **Processing Container** | `TriferProcessingContainerLight` | `#FEF0D5` | Latar lembut untuk indikator transkripsi/unduh berjalan. |
| **On Processing Container** | `TriferOnProcessingContainerLight` | `#4D2602` | Cokelat amber gelap untuk teks status proses. |

---

## 2. Mode Gelap (Dark Mode)

| Peran Token | Nama Kotlin | Hex | Alasan Pemilihan |
|---|---|---|---|
| **Primary** | `TriferPrimaryDark` | `#52D1CA` | Teal cerah lembut yang tetap kontras tanpa berpendar (halation) di ruang gelap. |
| **On Primary** | `TriferOnPrimaryDark` | `#043335` | Teal sangat gelap untuk teks di atas tombol primary mode gelap. |
| **Primary Container** | `TriferPrimaryContainerDark` | `#0D4D50` | Latar teal dalam untuk chip/sorotan aktif di mode gelap. |
| **On Primary Container** | `TriferOnPrimaryContainerDark` | `#CCF2F0` | Teks teal sangat terang di atas container gelap. |
| **Secondary** | `TriferSecondaryDark` | `#93B7DC` | Biru slate lembut untuk aksi sekunder dan elemen AI. |
| **On Secondary** | `TriferOnSecondaryDark` | `#0F273D` | Biru gelap untuk teks di atas tombol sekunder. |
| **Secondary Container** | `TriferSecondaryContainerDark` | `#233C54` | Latar kartu sekunder / panel chat AI mode gelap. |
| **On Secondary Container** | `TriferOnSecondaryContainerDark` | `#DCE8F5` | Teks biru sangat terang. |
| **Aksen Sedang Merekam** | `TriferRecordingDark` | `#FF6B5B` | Coral terang agar indikator & teks status rekaman menonjol di layar gelap. |
| **On Recording** | `TriferOnRecordingDark` | `#450B06` | Marun sangat gelap untuk teks di atas `#FF6B5B`. *(Catatan: tombol Rekam solid dapat tetap memakai `#D13426` dengan ikon `#FFFFFF` agar konsisten).* |
| **Recording Container** | `TriferRecordingContainerDark` | `#691811` | Latar merah gelap untuk pill status rekaman. |
| **On Recording Container** | `TriferOnRecordingContainerDark` | `#FFDAD6` | Teks coral pucat kontras tinggi. |
| **Background** | `TriferBackgroundDark` | `#0D1317` | Slate-arang sangat gelap (lebih nyaman daripada `#000000` murni di kelas gelap). |
| **On Background** | `TriferOnBackgroundDark` | `#E9F0F2` | Putih lembut (off-white) agar tidak menyilaukan mata. |
| **Surface** | `TriferSurfaceDark` | `#151E24` | Permukaan kartu catatan dengan elevasi visual yang jelas. |
| **On Surface** | `TriferOnSurfaceDark` | `#E9F0F2` | Teks transkrip live di atas surface gelap. |
| **Surface Variant** | `TriferSurfaceVariantDark` | `#202D35` | Area input, panel kontrol bawah, dan kartu sekunder. |
| **On Surface Variant** | `TriferOnSurfaceVariantDark` | `#A8BAC0` | Teks sekunder (durasi, cap waktu, petunjuk). |
| **Outline** | `TriferOutlineDark` | `#647880` | Garis tepi dan pemisah halus di mode gelap. |
| **Status: Sukses** | `TriferSuccessDark` | `#4ADE80` | Hijau terang ramah mata di latar gelap. |
| **On Success** | `TriferOnSuccessDark` | `#052E16` | Hijau sangat gelap untuk teks di atas badge sukses. |
| **Success Container** | `TriferSuccessContainerDark` | `#134729` | Latar status sukses mode gelap. |
| **On Success Container** | `TriferOnSuccessContainerDark` | `#C6F6D5` | Teks hijau pucat kontras tinggi. |
| **Status: Gagal** | `TriferErrorDark` | `#F87171` | Merah lembut tapi jelas untuk pesan error di mode gelap. |
| **On Error** | `TriferOnErrorDark` | `#450A0A` | Merah sangat gelap di atas badge error. |
| **Error Container** | `TriferErrorContainerDark` | `#6B1414` | Latar banner kesalahan mode gelap. |
| **On Error Container** | `TriferOnErrorContainerDark` | `#FEE2E2` | Teks merah pucat kontras tinggi. |
| **Status: Proses** | `TriferProcessingDark` | `#FBBF24` | Amber cerah untuk proses transkripsi / unduh model / jeda. |
| **On Processing** | `TriferOnProcessingDark` | `#3D2102` | Cokelat gelap di atas badge proses. |
| **Processing Container** | `TriferProcessingContainerDark` | `#543205` | Latar indikator proses mode gelap. |
| **On Processing Container** | `TriferOnProcessingContainerDark` | `#FEF3C7` | Teks kuning-krem kontras tinggi. |

---

## 3. Verifikasi Rasio Kontras (WCAG 2.1)

Semua pasangan teks/latar utama di bawah ini telah dihitung dengan rumus luminansi relatif WCAG 2.1 dan memenuhi syarat minimum **WCAG AA (>= 4.5:1 untuk teks normal, >= 3.0:1 untuk teks besar & komponen UI)**.

### Mode Terang
| Pasangan (Foreground / Background) | Rasio Kontras | Level |
|---|---|---|
| `OnBackground` (`#111A1E`) di atas `Background` (`#F6F8F9`) | **16.56 : 1** | AAA |
| `OnSurface` (`#111A1E`) di atas `Surface` (`#FFFFFF`) | **17.64 : 1** | AAA |
| `OnSurfaceVariant` (`#44545A`) di atas `Surface` (`#FFFFFF`) | **7.89 : 1** | AAA |
| `OnSurfaceVariant` (`#44545A`) di atas `SurfaceVariant` (`#E7EEF0`) | **6.72 : 1** | AA |
| `Primary` (`#0F6B6E`) di atas `Background` (`#F6F8F9`) | **5.89 : 1** | AA |
| `Primary` (`#0F6B6E`) di atas `Surface` (`#FFFFFF`) | **6.27 : 1** | AA |
| `OnPrimary` (`#FFFFFF`) di atas `Primary` (`#0F6B6E`) | **6.27 : 1** | AA |
| `OnPrimaryContainer` (`#063739`) di atas `PrimaryContainer` (`#D2F1F0`) | **10.90 : 1** | AAA |
| `OnSecondary` (`#FFFFFF`) di atas `Secondary` (`#365573`) | **7.77 : 1** | AAA |
| `OnSecondaryContainer` (`#13293D`) di atas `SecondaryContainer` (`#DCE8F5`) | **11.97 : 1** | AAA |
| `RecordingAccent` (`#D13426`) di atas `Surface` (`#FFFFFF`) | **4.96 : 1** | AA |
| `OnRecordingAccent` (`#FFFFFF`) di atas `RecordingAccent` (`#D13426`) | **4.96 : 1** | AA |
| `OnRecordingContainer` (`#6E140C`) di atas `RecordingContainer` (`#FDE3E0`) | **9.75 : 1** | AAA |
| `OnSuccess` (`#FFFFFF`) di atas `Success` (`#16794B`) | **5.43 : 1** | AA |
| `OnSuccessContainer` (`#0A3D24`) di atas `SuccessContainer` (`#D7F3E3`) | **10.45 : 1** | AAA |
| `OnError` (`#FFFFFF`) di atas `Error` (`#B91C1C`) | **6.47 : 1** | AA |
| `OnErrorContainer` (`#660E0E`) di atas `ErrorContainer` (`#FEE2E2`) | **10.52 : 1** | AAA |
| `OnProcessing` (`#FFFFFF`) di atas `Processing` (`#A15607`) | **5.46 : 1** | AA |
| `OnProcessingContainer` (`#4D2602`) di atas `ProcessingContainer` (`#FEF0D5`) | **11.70 : 1** | AAA |

### Mode Gelap
| Pasangan (Foreground / Background) | Rasio Kontras | Level |
|---|---|---|
| `OnBackground` (`#E9F0F2`) di atas `Background` (`#0D1317`) | **16.22 : 1** | AAA |
| `OnSurface` (`#E9F0F2`) di atas `Surface` (`#151E24`) | **14.65 : 1** | AAA |
| `OnSurfaceVariant` (`#A8BAC0`) di atas `Surface` (`#151E24`) | **8.41 : 1** | AAA |
| `OnSurfaceVariant` (`#A8BAC0`) di atas `SurfaceVariant` (`#202D35`) | **7.02 : 1** | AAA |
| `Primary` (`#52D1CA`) di atas `Background` (`#0D1317`) | **10.09 : 1** | AAA |
| `Primary` (`#52D1CA`) di atas `Surface` (`#151E24`) | **9.12 : 1** | AAA |
| `OnPrimary` (`#043335`) di atas `Primary` (`#52D1CA`) | **7.41 : 1** | AAA |
| `OnPrimaryContainer` (`#CCF2F0`) di atas `PrimaryContainer` (`#0D4D50`) | **7.99 : 1** | AAA |
| `OnSecondary` (`#0F273D`) di atas `Secondary` (`#93B7DC`) | **7.29 : 1** | AAA |
| `OnSecondaryContainer` (`#DCE8F5`) di atas `SecondaryContainer` (`#233C54`) | **9.16 : 1** | AAA |
| `RecordingAccent` (`#FF6B5B`) di atas `Surface` (`#151E24`) | **6.04 : 1** | AA |
| `OnRecordingAccent` (`#450B06`) di atas `RecordingAccent` (`#FF6B5B`) | **5.76 : 1** | AA |
| `OnRecordingContainer` (`#FFDAD6`) di atas `RecordingContainer` (`#691811`) | **9.34 : 1** | AAA |
| `OnSuccess` (`#052E16`) di atas `Success` (`#4ADE80`) | **8.55 : 1** | AAA |
| `OnSuccessContainer` (`#C6F6D5`) di atas `SuccessContainer` (`#134729`) | **8.95 : 1** | AAA |
| `OnError` (`#450A0A`) di atas `Error` (`#F87171`) | **5.84 : 1** | AA |
| `OnErrorContainer` (`#FEE2E2`) di atas `ErrorContainer` (`#6B1414`) | **9.91 : 1** | AAA |
| `OnProcessing` (`#3D2102`) di atas `Processing` (`#FBBF24`) | **8.88 : 1** | AAA |
| `OnProcessingContainer` (`#FEF3C7`) di atas `ProcessingContainer` (`#543205`) | **10.28 : 1** | AAA |
