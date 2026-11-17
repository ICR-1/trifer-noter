# Catatan Animasi Tombol Rekam — Trifer Noter (A10)

Dokumen ini melengkapi aset vektor `btn_record_idle.xml`, `btn_record_active.xml`, dan `btn_record_paused.xml` (ukuran kanvas seragam `88dp × 88dp` agar transisi antar-status bebas *layout jump*).

---

## 1. Transisi Antar-Status (`Idle` ↔ `Active` ↔ `Paused`)

| Transisi | Properti | Durasi | Easing / Spec | Catatan Implementasi |
|---|---|---|---|---|
| **Sentuh / Tekan (`Press`)** | `scale` | `100 ms` | `FastOutSlowInEasing` | Kecilkan tombol inti ke `0.94f` saat ditekan, kembalikan ke `1.0f` saat dilepas (`spring(dampingRatio = 0.65f, stiffness = 500f)`). |
| **Idle → Active** | Warna lingkaran inti (`#0F6B6E` → `#D13426`) | `220 ms` | `FastOutSlowInEasing` | Gunakan `animateColorAsState` agar perpindahan teal ke aksen coral-merah terasa mantap namun tidak mengejutkan di ruang kelas. |
| **Idle ↔ Active / Paused (Ikon Dalam)** | `alpha` + `scale` | `180 ms` | `FastOutSlowInEasing` | Gunakan `AnimatedContent` dengan `fadeIn(180ms) + scaleIn(initialScale = 0.82f)` dan `fadeOut(120ms) + scaleOut(targetScale = 0.82f)`. |
| **Active → Paused** | Warna inti (`#D13426` → `#A15607`) | `200 ms` | `FastOutSlowInEasing` | Lingkaran luar berkedip dihentikan perlahan (`alpha` cincin pulse turun ke `0f` dalam `200 ms`), diganti halo `#FEF0D5`. |

---

## 2. Animasi Denyut Saat Merekam (`Active` Pulse Ring)

Pada status `btn_record_active`, dua cincin luar (`r = 38dp` dan `r = 43dp`) sebaiknya dianimasikan secara terpisah di Compose/View di belakang lingkaran inti `64dp`:

1. **Denyut Napas Lembut (*Ambient Breathing Pulse*)**:
   - **Durasi**: `1400 ms` per siklus (`RepeatMode.Reverse` atau *ripple outward* `1600 ms` `RepeatMode.Restart`).
   - **Easing**: `CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)` (`FastOutSlowInEasing`) untuk *breathing*, atau `LinearOutSlowInEasing` untuk *outward ripple*.
   - **Keyframe yang disarankan**:
     - Cincin dalam (`r=38dp`): `scale` `0.96f → 1.08f`, `alpha` `0.34f → 0.18f`.
     - Cincin luar (`r=43dp`, *staggered* +`350 ms` atau efek *ripple*): `scale` `0.85f → 1.18f`, `alpha` `0.26f → 0.0f`.
2. **Gelombang Suara Reaktif (Opsional)**:
   - Keempat batang suara putih di dalam tombol aktif dapat diskalakan secara vertikal (`scaleY` antara `0.45f` hingga `1.15f`) mengikuti amplitudo mikrofon (*RMS audio level*) dengan `animateFloatAsState(spring(stiffness = 600f, dampingRatio = 0.7f))` atau `tween(90ms, LinearEasing)` agar terasa benar-benar *live*.

---

## 3. Aksesibilitas & Haptik

- **Haptic Feedback**: Berikan `HapticFeedbackType.LongPress` (atau `Confirm` pada API 30+) saat transisi `Idle → Active`, dan `TextHandleMove` / klik ringan saat `Active ↔ Paused`.
- **Reduce Motion**: Jika `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (pengguna mematikan animasi sistem), tampilkan status statis `btn_record_active.xml` tanpa denyut berulang.
- **Label Aksesibilitas (`contentDescription`)**:
  - Idle: `"Mulai merekam"`
  - Active: `"Sedang merekam, ketuk untuk jeda"` *(atau `"Hentikan rekaman"` sesuai alur kontrol)*
  - Paused: `"Rekaman dijeda, ketuk untuk lanjutkan"`
