package com.example.notetaker.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** Aplikasi di HP yang dapat menerima teks lewat ACTION_SEND. [isAi] true bila dikenali/diduga aplikasi AI. */
data class AiApp(val packageName: String, val label: String, val icon: Drawable?, val isAi: Boolean)

/**
 * Deteksi otomatis aplikasi AI. Semua aplikasi yang menerima `text/plain` dicari (deklarasi `<queries>` berbasis
 * intent di manifest, tanpa izin QUERY_ALL_PACKAGES), lalu ditandai sebagai AI bila:
 *  - paketnya ada di daftar [KNOWN], atau
 *  - nama/paketnya mengandung kata kunci AI ([looksLikeAi]), atau
 *  - disematkan pengguna lewat "Lainnya…" (untuk aplikasi AI baru yang tidak dikenali).
 */
object AiApps {
    /** Paket yang dikenal beserta nama tampil. Urutan = urutan tampil. */
    val KNOWN: List<Pair<String, String>> = listOf(
        "com.openai.chatgpt" to "ChatGPT",
        "com.anthropic.claude" to "Claude",
        "com.google.android.apps.bard" to "Gemini",
        "com.deepseek.chat" to "DeepSeek",
        "ai.perplexity.app.android" to "Perplexity",
        "com.microsoft.copilot" to "Copilot",
        "ai.x.grok" to "Grok",
        "com.mistral.chat" to "Le Chat",
        // Namanya ("Notebook") tidak mengandung kata kunci AI, jadi didaftarkan eksplisit.
        "com.google.android.apps.labs.language.tailwind" to "NotebookLM",
    )

    // Kata utuh, supaya "kimia" tidak cocok dengan "kimi" dan "Bardiya" tidak cocok dengan "bard".
    private val KEYWORDS = Regex(
        """\b(chatgpt|gpt|openai|claude|anthropic|gemini|bard|deepseek|perplexity|copilot|grok|mistral|qwen|kimi|moonshot|chatbot|llama|poe)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Kata "AI" berdiri sendiri pada nama ("Meta AI") atau sebagai segmen nama paket ("ai.perplexity..."). */
    private val AI_WORD = Regex("""(^|[\s._\-])ai($|[\s._\-])""", RegexOption.IGNORE_CASE)

    /** Dugaan berdasarkan nama tampil dan nama paket. */
    fun looksLikeAi(packageName: String, label: String): Boolean =
        KEYWORDS.containsMatchIn(label) || KEYWORDS.containsMatchIn(packageName) ||
            AI_WORD.containsMatchIn(label) || AI_WORD.containsMatchIn(packageName)

    /**
     * Cari semua aplikasi penerima teks. Hasil: (aplikasi AI, aplikasi lain). Aplikasi ini sendiri dikeluarkan.
     * [pinned] = paket yang disematkan pengguna sebagai AI.
     */
    fun detect(context: Context, pinned: Set<String>): Pair<List<AiApp>, List<AiApp>> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_SEND).setType("text/plain")
        val byPackage = LinkedHashMap<String, AiApp>()
        for (ri in runCatching { pm.queryIntentActivities(probe, 0) }.getOrDefault(emptyList())) {
            val info = ri.activityInfo?.applicationInfo ?: continue
            val pkg = info.packageName
            if (pkg == context.packageName || pkg in byPackage) continue
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg).ifBlank { pkg }
            val isAi = KNOWN.any { it.first == pkg } || pkg in pinned || looksLikeAi(pkg, label)
            byPackage[pkg] = AiApp(pkg, label, runCatching { pm.getApplicationIcon(info) }.getOrNull(), isAi)
        }
        val order = KNOWN.map { it.first }
        val (ai, others) = byPackage.values.partition { it.isAi }
        return ai.sortedWith(
            compareBy<AiApp> { order.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { it.label.lowercase() },
        ) to others.sortedBy { it.label.lowercase() }
    }

    /** Buka [packageName] langsung dengan teks terisi. Mengembalikan false bila aplikasi tidak bisa menerimanya. */
    fun send(context: Context, packageName: String, subject: String, text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(packageName)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
