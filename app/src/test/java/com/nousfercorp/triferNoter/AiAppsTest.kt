package com.nousfercorp.triferNoter

import com.nousfercorp.triferNoter.ai.AiApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiAppsTest {
    @Test fun `nama paket dan nama tampil unik`() {
        val pkgs = AiApps.KNOWN.map { it.first }
        assertEquals(pkgs.size, pkgs.toSet().size)
        assertEquals(AiApps.KNOWN.size, AiApps.KNOWN.map { it.second }.toSet().size)
    }

    private fun manifest(): String =
        listOf("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml").map { File(it) }.first { it.exists() }.readText()

    /** Tanpa deklarasi <queries>, aplikasi AI tidak terlihat di Android 11+ dan tombolnya tidak akan muncul. */
    @Test fun `setiap paket dikenal dideklarasikan di queries pada manifest`() {
        val m = manifest()
        for ((pkg, _) in AiApps.KNOWN) {
            assertTrue("manifest belum mendeklarasikan $pkg", m.contains("<package android:name=\"$pkg\" />"))
        }
    }

    /** Dasar deteksi otomatis: semua penerima teks harus terlihat tanpa izin QUERY_ALL_PACKAGES. */
    @Test fun `manifest mendeklarasikan intent SEND teks dan tidak memakai QUERY_ALL_PACKAGES`() {
        val m = manifest()
        assertTrue(m.contains("android.intent.action.SEND"))
        assertTrue(m.contains("android:mimeType=\"text/plain\""))
        assertFalse(m.contains("QUERY_ALL_PACKAGES"))
    }

    @Test fun `aplikasi AI dikenali dari nama atau paket`() {
        val ai = listOf(
            "com.openai.chatgpt" to "ChatGPT",
            "com.anthropic.claude" to "Claude",
            "com.google.android.apps.bard" to "Gemini",
            "com.deepseek.chat" to "DeepSeek",
            "ai.perplexity.app.android" to "Perplexity",
            "com.microsoft.copilot" to "Microsoft Copilot",
            "ai.x.grok" to "Grok",
            "com.mistral.chat" to "Le Chat",
            "com.alibaba.qwen" to "Qwen",
            "com.meta.ai" to "Meta AI",
            "com.xyz.baru" to "Asisten AI Baru",
            "com.moonshot.kimi" to "Kimi",
        )
        for ((pkg, label) in ai) assertTrue("$label ($pkg) seharusnya AI", AiApps.looksLikeAi(pkg, label))
    }

    @Test fun `aplikasi biasa tidak salah dikira AI`() {
        val notAi = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.google.android.gm" to "Gmail",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.docs" to "Drive",
            "com.google.android.keep" to "Keep",
            "com.samsung.android.app.notes" to "Samsung Notes",
            "com.google.android.apps.messaging" to "Pesan",
            "com.instagram.android" to "Instagram",
            "id.kimia.belajar" to "Kimia Dasar",
            "com.bardiya.news" to "Bardiya News",
            "com.android.mail" to "Email",
            "com.fair.share" to "Fair Share",
            "com.example.maps" to "Peta",
        )
        for ((pkg, label) in notAi) assertFalse("$label ($pkg) bukan AI", AiApps.looksLikeAi(pkg, label))
    }
}
