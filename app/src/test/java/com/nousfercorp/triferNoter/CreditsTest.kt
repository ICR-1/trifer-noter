package com.nousfercorp.triferNoter

import com.nousfercorp.triferNoter.about.Credits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreditsTest {
    private fun raw(name: String): File =
        listOf("app/src/main/res/raw/$name.txt", "src/main/res/raw/$name.txt").map { File(it) }.firstOrNull { it.exists() }
            ?: File("tidak-ada-$name")

    @Test fun `setiap kredit punya berkas lisensi asli yang tidak kosong`() {
        for (c in Credits.ALL) {
            val f = raw(c.licenseRaw)
            assertTrue("berkas lisensi ${c.licenseRaw} untuk ${c.name} tidak ada", f.exists())
            assertTrue("lisensi ${c.licenseRaw} terlalu pendek", f.readText().length > 500)
        }
    }

    @Test fun `jenis lisensi pada kredit cocok dengan isi berkas`() {
        for (c in Credits.ALL) {
            val text = raw(c.licenseRaw).readText()
            if (c.license.startsWith("MIT")) assertTrue("${c.name}: bukan teks MIT", text.contains("MIT License") && text.contains("Copyright"))
            if (c.license.startsWith("Apache")) assertTrue("${c.name}: bukan teks Apache", text.contains("Apache License") && text.contains("Version 2.0"))
        }
    }

    @Test fun `lisensi aplikasi adalah Apache 2_0 dan pembuat tercantum di NOTICE`() {
        val text = raw(Credits.APP_LICENSE).readText()
        assertTrue(text.contains("Apache License") && text.contains("Version 2.0"))
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText()
        assertTrue(notice.contains(Credits.MAKER))
        assertTrue(notice.contains("Apache License"))
    }

    @Test fun `salinan lisensi di aplikasi sama dengan LICENSE di akar proyek`() {
        val root = listOf("LICENSE", "../LICENSE").map { File(it) }.first { it.exists() }
        assertEquals(root.readText(), raw(Credits.APP_LICENSE).readText())
    }

    @Test fun `nama kredit unik dan komponen utama tercantum`() {
        val names = Credits.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (must in listOf("sherpa-onnx", "whisper.cpp", "ONNX Runtime", "PengChengStarling", "Whisper")) {
            assertTrue("kredit $must hilang", must in names)
        }
    }
}
