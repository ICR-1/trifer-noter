# Benchmark STT On-Device — M2a

| | |
|---|---|
| **Tanggal** | 2026-11-17 |
| **Perangkat** | Samsung Galaxy S23 Ultra (SM-S918B), Snapdragon 8 Gen 2 (SM8550), RAM 11 GB, Android 17 (SDK 37) |
| **Kondisi** | Dijalankan lewat `adb shell`, layar dikunci, USB tersambung (mengisi daya), suhu baterai 40–43 °C |
| **Skrip** | `tools/bench/bench.py`, `tools/bench/make_gap.py` (dapat diulang di perangkat lain) |
| **Engine diuji** | whisper.cpp (commit `aba02de`, build NDK 28.2, `-march=armv8.6-a`, CPU saja), sherpa-onnx v1.13.8 (CPU, int8) |
| **Data uji** | [FLEURS](https://huggingface.co/datasets/google/fleurs) Bahasa Indonesia (test): ucapan bacaan jernih, 15 ujaran (±3 menit) untuk uji akurasi, 30 ujaran + klip panjang untuk uji lain |

> **Peringatan keterbatasan.** FLEURS adalah ucapan bacaan yang jernih. WER di dokumen ini **lebih optimistis** daripada rekaman kelas/rapat nyata. Untuk pembanding realistis: model card PengChengStarling melaporkan WER **20,5%** di GigaSpeech2-Indonesia (ucapan nyata dari YouTube), vs 20,0% untuk Whisper-Large v3. Validasi akhir wajib memakai rekaman asli (lihat §7).

## 1. Metode

- **WER** dihitung per kata setelah huruf dikecilkan dan tanda baca dibuang (jarak Levenshtein), terhadap transkrip acuan FLEURS. Angka dan ejaan yang berbeda format (mis. "20" vs "dua puluh") ikut dihitung sebagai salah, sama untuk semua engine.
- **RTF** (real-time factor) = waktu proses ÷ durasi audio. RTF 0,5 berarti memproses 1 menit audio butuh 30 detik. Waktu muat model tidak dihitung.
- Semua uji memakai **4 thread** (terbukti tercepat, lihat §3), dekode **greedy** kecuali disebut lain.
- Audio diberikan **per ujaran** (setara dengan satu segmen hasil VAD). Pengujian audio panjang satu aliran dibahas di §4.

## 2. Akurasi dan kecepatan per ujaran (15 ujaran, 4 thread, greedy)

### whisper.cpp, jendela encoder penuh (30 detik)

| Model | Ukuran | WER | RTF | Proses per ujaran |
|---|---|---|---|---|
| tiny q5_1 | 31 MB | 48,7% | 0,13 | 1,5 dtk |
| base q5_1 | 57 MB | 27,3% | 0,33 | 3,8 dtk |
| small q5_1 | 181 MB | 13,3% | 1,06 | 11,8 dtk |
| small q8_0 | 252 MB | 12,7% | 0,46 | 5,1 dtk |
| medium q5_0 | 514 MB | 5,5% | 2,91 | 32,5 dtk |
| large-v3-turbo q5_0 | 547 MB | 4,5% | 4,58 | 52,9 dtk |

### whisper.cpp, jendela encoder dipendekkan (`-ac auto`: 50 frame/detik + 100)

| Model | WER | RTF | Proses per ujaran | Klip 3 dtk |
|---|---|---|---|---|
| base q5_1 | 26,6% | 0,11 | 1,3 dtk | 0,59 dtk |
| small q8_0 | 12,7% | **0,15** | 1,8 dtk | **0,48 dtk** |
| medium q5_0 | 3,3% | 1,33 | 15,7 dtk | n/a |
| **medium q8_0** | **4,9%** | **0,44** | 5,0 dtk | n/a |

### sherpa-onnx (streaming, int8)

| Model | WER | RTF | Catatan |
|---|---|---|---|
| **PengChengStarling multibahasa** (`zipformer ar/en/id/ja/ru/th/vi/zh`, 2025-02-10) | **8,8%** | **0,17** | Teks Indonesia ortografis, HURUF BESAR, tanpa tanda baca. Streaming sungguhan. |
| Bookbot `pruned-transducer-stateless7-streaming-id` | tidak terukur | 0,07 | Keluaran **fonetik IPA** ("jaŋ tiŋin pərsaldʒu"), bukan ejaan. **Dicoret.** |

## 3. Temuan teknis

1. **Kuantisasi q8_0 jauh lebih cepat daripada q5 di CPU ini.** small q8_0 lebih dari 2× lebih cepat daripada small q5_1; medium q8_0 sekitar **3× lebih cepat** daripada medium q5_0 (RTF 0,44 vs 1,33), dengan akurasi sedikit di bawahnya. Penyebabnya diperkirakan kernel int8 (`MATMUL_INT8`, `DOTPROD`, `REPACK`) hanya dioptimalkan untuk q8_0/q4_0. Pilih **q8_0**.
2. **Encoder Whisper selalu memproses 30 detik penuh** (klip 3 detik pun butuh 2,2 detik di tiny). Opsi `-ac` memperpendek jendela itu: small q8_0 jadi **3× lebih cepat tanpa penurunan WER** pada ujaran FLEURS (12,7% → 12,7%). Efek pada audio yang lebih rumit belum diukur.
3. **4 thread adalah titik optimal** untuk small q8_0: 2 thread RTF 0,23, 4 thread 0,15, 6 thread 0,18.
4. **Beam search** mengurangi WER small q8_0 dari 12,7% ke 11,0%, tapi RTF naik dari 0,15 ke 0,33. Tidak sepadan untuk live.
5. **large-v3-turbo terlalu lambat** di HP (RTF 4,6 pada q5_0) walau akurasinya tertinggi. Tidak dipakai.
6. **Suhu aman** (lihat §5).

## 4. Pemrosesan audio panjang: harus lewat segmentasi VAD

Klip gabungan 20 ujaran (±4 menit), tanpa/dengan jeda 0,8 detik:

| Pendekatan | WER (tanpa jeda / dengan jeda) | RTF |
|---|---|---|
| whisper small q8_0, satu kali jalan | 36,4% / 30,4% | 0,22–0,26 |
| whisper medium q8_0, satu kali jalan | 30,9% / 26,4% | 0,62–0,64 |
| whisper small q8_0 + VAD bawaan whisper.cpp (hanya klip dengan jeda) | tidak diuji / 59,1% | 0,24 |
| whisper medium q8_0 + VAD bawaan whisper.cpp (hanya klip dengan jeda) | tidak diuji / 46,6% | 0,60 |
| sherpa-onnx, satu aliran tanpa VAD | 71,3% / 66,1% | 0,16–0,18 |
| **sherpa-onnx + Silero VAD (segmen demi segmen)** | **9,0% / 9,0%** | **0,19 / 0,24** |

**Kesimpulan:** engine apa pun **kehilangan banyak teks bila diberi satu aliran panjang**. Whisper melewatkan ±26% kata (291 dari 395), dan sherpa menghilangkan sekitar 70%. Pipeline harus **memotong ucapan dengan VAD dan mendekode tiap segmen**. Dengan pendekatan itu sherpa stabil di 9,0% pada audio kontinu 4 menit. VAD bawaan whisper.cpp tidak dipakai. Whisper dijalankan per segmen hasil VAD milik kita sendiri (setara pengujian per ujaran di §2).

Cacat yang ditemukan: model sherpa multibahasa kadang mengeluarkan **aksara asing di awal segmen** (mis. "デンで", "ดูฝาก") ketika potongan VAD memotong awal kata. Solusi: filter token non-Latin (bahasa dikunci Indonesia), diterapkan di `bench.py` dan wajib di aplikasi.

## 5. Uji beban berkelanjutan (HP mengisi daya, layar dikunci)

**A. Live: sherpa-onnx + VAD, 10 putaran berturut-turut** (252 detik audio per putaran, tanpa jeda antar putaran, jadi ±5× lebih berat daripada beban live nyata):

| Putaran | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| RTF | 0,23 | 0,22 | 0,21 | 0,22 | 0,22 | 0,21 | 0,21 | 0,21 | 0,22 | 0,21 |
| Suhu baterai (°C) | 42,6 | 42,4 | 42,5 | 42,6 | 42,6 | 42,6 | 42,7 | 42,7 | 42,8 | 42,9 |

**B. Rapikan: whisper medium q8_0 (`-ac auto`), 6 putaran × 15 ujaran:**

| Putaran | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| RTF | 0,50 | 0,48 | 0,48 | 0,47 | 0,48 | 0,47 |
| Suhu baterai (°C) | 42,9 | 43,0 | 43,1 | 43,1 | 43,3 | 43,3 |

**Tidak ada throttling:** kecepatan tidak turun dan suhu hanya naik ±0,3–0,7 °C selama ±18 menit. **Keterbatasan:** durasi uji hanya ±18 menit (bukan 3 jam), suhu yang diukur adalah suhu baterai (bukan suhu SoC), dan HP mengisi daya selama uji (konsumsi baterai belum terukur). **Uji 3 jam penuh tetap perlu dilakukan di aplikasi sungguhan** (M2b/M5).

## 6. Keputusan

| Peran | Engine | Model | Lisensi | Alasan |
|---|---|---|---|---|
| **Transkrip LIVE** | **sherpa-onnx** (streaming) | PengChengStarling multibahasa int8, ±340 MB | sherpa-onnx Apache-2.0, model **Apache-2.0** | Streaming sungguhan, WER 8,8% (lebih baik daripada whisper small 12,7%), RTF 0,17–0,24, tanpa trik jendela geser. Teks kata-per-kata muncul alami. |
| **VAD** | Silero VAD (via sherpa-onnx) | `silero_vad.onnx`, 0,6 MB | MIT | Dipakai di kedua jalur. |
| **Rapikan (setelah selesai)** | **whisper.cpp** | **medium q8_0**, ±823 MB, `-ac auto`, 4 thread, greedy, per segmen VAD | MIT | WER 4,9%, RTF ±0,47–0,50, menghasilkan **tanda baca dan huruf kapital**. 3 jam audio ≈ 1,5 jam di charger. |
| Dicoret | whisper tiny/base, small q5_1, medium q5_0, large-v3-turbo, sherpa Bookbot | | | Terlalu tidak akurat atau terlalu lambat. |

**Ukuran model di HP:** live ±340 MB + Rapikan ±823 MB = ±1,2 GB. Rapikan boleh opsional (unduh/impor hanya bila diinginkan).

**Dampak ke MVP:**
- MVP cukup dengan **sherpa-onnx + Silero VAD** untuk live. Whisper (Rapikan) dapat menyusul sebagai fitur setelahnya karena butuh pustaka native kedua.
- Keluaran sherpa berupa HURUF BESAR tanpa tanda baca. Tampilan live perlu normalisasi sederhana (huruf kecil, kapital di awal kalimat per segmen). Tanda baca penuh datang dari Rapikan.
- Wajib: filter token non-Latin; kunci bahasa Indonesia.
- Ukuran APK: pustaka native sherpa-onnx + onnxruntime (arm64) kira-kira 25–40 MB, **mendekati atau melewati target < 30 MB**; perlu dicek saat integrasi (R8, `abiFilters arm64-v8a`, atau ONNX Runtime yang dipangkas).

## 6b. Validasi di dalam aplikasi (M2b)

Mesin live yang sama dijalankan di aplikasi (`StreamingAsr`) pada klip 4 menit dengan jeda 0,8 detik antar ujaran (alat uji `DebugTranscribeActivity`, build debug). Audio diumpankan per 100 ms seperti `AudioRecord`.

| Konfigurasi | WER | Ucapan terdeteksi | RTF |
|---|---|---|---|
| `reset()` pada stream yang sama di tiap endpoint | **75,6%** | 9 dari ±20 | 0,13 |
| **Stream baru** di tiap endpoint, jeda 0,8 dtk, potong 15 dtk | 11,2% | 26 | 0,16 |
| Stream baru, jeda 0,5 dtk, potong 15 dtk | 22,4% | 55 | 0,20 |
| Stream baru, jeda 0,8 dtk, potong 25 dtk | 10,7% | 24 | 0,21 |
| Stream baru, jeda 1,2 dtk, potong 15 dtk | **10,2%** | 21 | 0,21 |

**Temuan:** memanggil `recognizer.reset(stream)` pada stream yang sama **menghilangkan sebagian besar teks berikutnya** (WER 75,6%), sedangkan membuat `createStream()` baru di tiap endpoint memulihkan akurasi ke ±10–11%. Ini sama dengan perilaku `sherpa-onnx-vad-with-online-asr` di §4 yang memakai stream baru per segmen. Jeda 0,5 detik terlalu agresif (memotong di tengah frasa). **Default produksi: stream baru per endpoint, jeda 1,2 dtk, potong paksa 20 dtk.**

RTF 0,13–0,21 di sini diukur saat HP juga dipakai untuk hal lain, jadi sedikit lebih tinggi daripada pengukuran CLI (0,16–0,19).

## 6c. Impor berkas audio (F14)

Klip 4 menit yang sama (jeda 0,8 dtk antar ujaran, 20 ujaran) dikonversi ke beberapa format dengan ffmpeg lalu diimpor lewat `ImportService` (jalur yang sama dengan pemilih berkas), dengan jeda endpoint 1,2 dtk.

| Format sumber | Status | Segmen | WER | Catatan |
|---|---|---|---|---|
| mp3 44,1 kHz stereo 128 kbps | selesai | 20 | 7,0% | ±40 dtk untuk 251,6 dtk audio |
| m4a (AAC) 48 kHz stereo 96 kbps | selesai | 20 | 9,0% | |
| ogg/opus 48 kHz 32 kbps | selesai | 20 | 8,0% | |
| WAV 8 kHz (kualitas telepon) | selesai | 21 | 13,0% | batas lebar pita memengaruhi akurasi |
| Berkas acak bukan audio / berkas teks | **gagal** dengan pesan "Berkas tidak dapat dibuka sebagai audio" | 0 | n/a | tanpa crash, service berhenti sendiri |

Durasi terbaca benar (251,6 dtk) di semua format. Tiga impor yang diantre berjalan berurutan tanpa tumpang tindih. WER di sini lebih rendah daripada rekaman langsung di §6b karena memakai jeda endpoint 1,2 dtk (default produksi) dan audio studio-jernih; **bukan** ukuran akurasi pada audio nyata.

## 6d. Teks "keputus" pada percakapan nyata (perbaikan 2026-11-18)

**Keluhan:** pada rekaman nyata (pembicara cepat, podcast ±107 dtk) kadang kata terpotong atau hilang. Analisis rekaman nyata menemukan tiga penyebab:

1. **Potongan paksa tiap 20 dtk di tengah kalimat** (aturan endpoint bawaan sherpa). Empat dari enam segmen tepat ±20 dtk, dan kata di titik potong rusak ("Subianto" → "Bianto", "kami" → "Ami").
2. **Jeda diukur dari token, padahal token model keluar terlambat ±0,6–1 dtk.** Saat aturan "tidak ada token baru" menyala, pembicara sebenarnya masih bicara, sehingga kata terduplikasi di titik potong.
3. **Stream "mogok" pada ucapan kontinu panjang:** pada stream ±17 dtk berhenti keluar token, ±4,7 dtk suara tanpa teks sama sekali ("lubang"), padahal stream baru yang diberi audio yang sama menghasilkan teks.

**Perbaikan (`StreamingAsr`, `PauseDetector`, `EndpointPolicy`):**
- Jeda diukur dari **energi audio** (frame 20 ms, ≥12 dB di bawah tingkat bicara baru-baru ini), bukan dari token.
- Makin panjang ucapan, makin pendek jeda yang cukup untuk memotong: ≥0,9 dtk (<6 dtk), 0,5 dtk (≥6), 0,3 dtk (≥9), 0,25 dtk (≥12). Batas minimum 0,25 dtk karena penutupan konsonan di dalam kata ("dianggap|nya") bisa sampai ±150–200 ms. **Batas keras 14 dtk** menghindari "mogok".
- Stream baru **dipanaskan dengan 1 dtk audio sebelumnya**, dan token yang timestamp-nya di wilayah tumpang tindih dibuang, supaya kata pertama tidak rusak dan tidak ada kata ganda.
- Ekor senyap ditambahkan sebelum mengunci teks agar token terakhir yang tertahan ikut keluar.

| Konfigurasi | Rekaman nyata: selisih vs Whisper medium | Rekaman nyata: lubang | FLEURS WER |
|---|---|---|---|
| Lama: endpoint bawaan, potong paksa 20 dtk | 14,4% | kata rusak di tiap potongan | 10,7% |
| Jeda dari token (0,16–0,9 dtk), batas 28 dtk | 11,0% | kata ganda, serpihan ("Nya", "Aya") | n/a |
| Jeda dari energi, batas 30 dtk | 12,7% | **±4,7 dtk hilang (50–52 dtk)** | 7,2% |
| **Jeda dari energi, batas 14 dtk (default)** | **11,0%** | **tidak ada** | **6,7%** |

Catatan: "selisih vs Whisper" memakai transkrip Whisper medium q8_0 sebagai pembanding (bukan kebenaran mutlak, Whisper juga punya salah), jadi angkanya hanya penunjuk arah. Ukuran yang lebih langsung adalah jumlah detik bersuara tanpa token (`tools`/`holes.py` di catatan kerja), yaitu 0 setelah perbaikan. Sisa kekurangan yang terlihat: satu kata ganda di sebuah titik potong ("di sini | Sini yang") dan satu huruf hilang di titik potong lain ("mengurusi" → "mengurus").

## 6e. Rapikan dengan Whisper medium q8_0 (F3b, 2026-11-18)

whisper.cpp dibangun di dalam aplikasi (NDK, `-march=armv8.6-a`, CPU saja, `libtrifer_whisper.so` 2,8 MB) dan dipakai untuk memproses ulang audio tersimpan. Audio dipotong ≤20 dtk di jeda, tiap potongan diproses Whisper (jendela encoder penuh, greedy), lalu transkrip diganti secara atomik.

**Hasil pada audio FLEURS 4 menit (acuan pasti):**

| Tahap | WER | Kata hasil / acuan | Bagian hilang |
|---|---|---|---|
| Live (sherpa-onnx) | 10,5% | 374 / 401 | beberapa kalimat |
| Rapikan, percobaan 1 (potongan ≤27 dtk, ambang bawaan Whisper) | 18,2% | 349 / 401 | **±21 dtk + ±14 dtk** |
| Rapikan, + lanjutkan sisa + ambang global | 8,5–12,7% | 374–397 / 401 | 1–2 ucapan utuh |
| **Rapikan akhir** (topeng suara lokal + pengisian celah) | **5,5%** | **401 / 401** | **tidak ada** |

**Rekaman asli (podcast, 153 dtk):** kalimat "Presiden mengatakan **A**, … mengatakan **B** dan lain sebagainya" yang pada live kehilangan huruf "A" kini lengkap, ditambah tanda baca, huruf kapital, dan angka ("75%", "67%").

**Tiga masalah yang ditemukan dan diperbaiki:**
1. **Whisper berhenti dini** pada jendela berisi beberapa kalimat dan dengan jeda panjang: hanya kalimat pertama yang keluar, ±21 dtk sisanya dilewati. Ambang lompat-diam bawaan (`no_speech_thold`, `logprob_thold`) dimatikan, dan **pengisian celah**: setelah satu potongan diproses, cari ucapan yang belum tercakup segmen (dari energi audio) lalu proses ulang celahnya.
2. **Topeng suara global keliru** (kesalahan saya sendiri): ambang tunggal untuk seluruh berkas menganggap ujaran pelan sebagai senyap (71 dari 236 dtk ucapan terlewat). Akibatnya ucapan pelan dilewati perencana potongan, dan segmen Whisper yang benar dibuang penyaring halusinasi. Diganti ambang **lokal** (jendela ±2,5 dtk, persentil 90): ambang ketat (16 dB) untuk mencari jeda, longgar (24 dB) untuk "adakah suara di sini?" (terdeteksi 220 dari 236 dtk).
3. **Halusinasi khas Whisper** ("Terima kasih telah menonton" di awal potongan): frasa penutup video yang sudah dikenal dibuang, kalimat sah seperti "terima kasih atas bantuannya" dibiarkan.

**Kecepatan:** RTF ±0,6–0,9 pada HP dingin (252 dtk audio ≈ 160–220 dtk). Setelah berjam-jam beban uji beruntun HP masuk throttling termal sedang (`Thermal Status: 2`) dan proses melambat ±2×. **Rapikan 3 jam audio** kira-kira 2–3 jam; wajib sambil mengisi daya dan perlu diuji dengan beban nyata.

**Sisa kekurangan:** nama asing dan nama diri tidak tepat ("Prabu Subianto" untuk "Prabowo Subianto", "Danoplitvise"), angka dituliskan sebagai digit, dan satu-dua kata pendek di awal potongan kadang hilang. Kosakata kustom (P1) menjadi solusi untuk nama.

## 6f. Kasus "b" vs "beda" dan percobaan petunjuk (`initial_prompt`)

**Temuan dari pemakaian nyata:** pada kalimat "…presiden mengatakan **A**, … presiden mengatakan **B** dan lain sebagainya", mesin live kehilangan "A" tetapi benar pada "b dan", sedangkan Rapikan (Whisper) menangkap "A" tetapi menulis "**beda** dan lain sebagainya" (atau "bedan lain"). Huruf "B" diucapkan "be", sama dengan awal kata "beda", dan model bahasa Whisper memilih kata yang lebih umum. Ini ambiguitas bawaan, bukan galat pemotongan.

**Percobaan:** memberikan teks live sebagai `initial_prompt` Whisper (supaya ejaan "b dan" dan "Prabowo" terbawa). Diuji pada tiga salinan rekaman yang sama (130 dtk), masing-masing dengan teks live yang sama persis.

| Mode petunjuk | Kata hasil | Tanda baca | "A" | "B" | "Prabowo Subianto" |
|---|---|---|---|---|---|
| **Tanpa petunjuk (dipakai)** | **305** | **47** | benar | "beda dan" | 1 dari 3, sisanya "Prabu" |
| Teks live pada rentang potongan (OWN) | 204 | 12 | salah tempat | "beda dan" | 2 |
| Teks live sebelum potongan (PREVIOUS) | 275 | 15 | hilang | "beda dan" | 2 |

**Kesimpulan:** petunjuk dari teks live **merugikan**. Whisper meniru gaya petunjuknya (huruf kecil tanpa tanda baca), kehilangan ±100 kata, dan tetap menulis "beda dan". Mode default tetap `NONE`. Kode `RefinePrompt`/`PromptMode` dipertahankan hanya sebagai kerangka percobaan, tidak aktif.

**Solusi yang dipakai:** **ubah teks per segmen** di layar detail (ikon pensil). Segmen yang diubah ditandai `edited` dan **dipertahankan** ketika Rapikan dijalankan lagi (segmen baru yang tumpang tindih dengannya dibuang). Diuji lewat jalur data pada salinan: segmen "…mengatakan b dan lain sebagainya." bertahan setelah Rapikan kedua, 33 segmen, 1 diedit, tanpa tumpang tindih.

## 7. Yang belum diukur (wajib sebelum rilis)

1. **Rekaman nyata** (kuliah/rapat, ruang bergema, banyak pembicara): rekam 2–3 menit asli dan ulangi benchmark. Perkiraan WER nyata ±20%.
2. **Uji 3 jam penuh di aplikasi**: kecepatan, suhu SoC, konsumsi baterai tanpa charger.
3. **Memori puncak** (RSS) sherpa dan whisper medium q8_0.
4. **Variasi `-ac` pada audio rumit** dan segmen > 15 detik untuk Rapikan.
5. **Perangkat lain** selain SM-S918B.
6. **Verifikasi integrasi JNI/Kotlin** sherpa-onnx (AAR/JNI resmi) dan latensi end-to-end di aplikasi (target: kata muncul ≤ 2 detik setelah diucapkan).

## 8. Cara mengulang

```text
# di folder kerja W (model di W/models, FLEURS di W/fleurs_raw, binary di W/build-android/bin)
python tools/bench/bench.py prep   --work W --n 30
python tools/bench/make_gap.py     --work W --gap 0.8
python tools/bench/bench.py push   --work W --binary W/build-android/bin/whisper-cli --models small-q8_0 medium-q8_0
python tools/bench/bench.py run    --work W --models small-q8_0 --threads 4 --beam 1 --ac auto --which utt --limit 15
python tools/bench/bench.py sherpa --work W --model sherpa-multi-vad --threads 4 --which long_gap
python tools/bench/bench.py report --work W
```
