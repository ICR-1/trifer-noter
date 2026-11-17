package com.example.notetaker.about

/** Satu komponen pihak ketiga yang dipakai aplikasi, beserta lisensinya. [licenseRaw] = nama berkas di res/raw. */
data class Credit(
    val name: String,
    val author: String,
    val role: String,
    val license: String,
    val licenseRaw: String,
)

/** Daftar kredit. Hanya komponen yang benar-benar ada di aplikasi. */
object Credits {
    /** Lisensi Trifer Noter sendiri (salinan LICENSE di akar proyek). */
    const val APP_LICENSE = "license_trifer_noter"
    const val MAKER = "Nousfercorp"
    const val APACHE = "license_apache_2_0"
    const val MIT_WHISPER_CPP = "license_mit_whisper_cpp"
    const val MIT_ONNXRUNTIME = "license_mit_onnxruntime"
    const val MIT_OPENAI_WHISPER = "license_mit_openai_whisper"

    val ALL: List<Credit> = listOf(
        Credit(
            name = "sherpa-onnx",
            author = "k2-fsa",
            role = "Mesin transkripsi langsung (streaming) di perangkat",
            license = "Apache License 2.0",
            licenseRaw = APACHE,
        ),
        Credit(
            name = "PengChengStarling",
            author = "PengCheng Lab",
            role = "Model suara untuk transkripsi langsung, termasuk Bahasa Indonesia",
            license = "Apache License 2.0",
            licenseRaw = APACHE,
        ),
        Credit(
            name = "ONNX Runtime",
            author = "Microsoft",
            role = "Pustaka penjalan model di perangkat",
            license = "MIT License",
            licenseRaw = MIT_ONNXRUNTIME,
        ),
        Credit(
            name = "whisper.cpp",
            author = "Georgi Gerganov dan kontributor ggml",
            role = "Mesin untuk fitur Rapikan",
            license = "MIT License",
            licenseRaw = MIT_WHISPER_CPP,
        ),
        Credit(
            name = "Whisper",
            author = "OpenAI",
            role = "Model suara untuk fitur Rapikan",
            license = "MIT License",
            licenseRaw = MIT_OPENAI_WHISPER,
        ),
        Credit(
            name = "Android Jetpack (Compose, Room, Navigation, Lifecycle, Activity, Core)",
            author = "The Android Open Source Project",
            role = "Tampilan, penyimpanan catatan, dan navigasi",
            license = "Apache License 2.0",
            licenseRaw = APACHE,
        ),
        Credit(
            name = "Kotlin dan Kotlin Coroutines",
            author = "JetBrains",
            role = "Bahasa pemrograman dan pemrosesan latar belakang",
            license = "Apache License 2.0",
            licenseRaw = APACHE,
        ),
    )
}
