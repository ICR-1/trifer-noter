package com.example.notetaker.ai

import android.content.Context
import androidx.core.content.edit

/** Preferensi layar "Kirim ke AI": pilihan terakhir diingat, dan pemberitahuan privasi hanya sekali. */
class AiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("trifer_ai", Context.MODE_PRIVATE)

    var template: AiTemplate
        get() = AiTemplate.fromId(prefs.getString(KEY_TEMPLATE, null))
        set(v) = prefs.edit { putString(KEY_TEMPLATE, v.id) }

    var customPrompt: String
        get() = prefs.getString(KEY_CUSTOM, "") ?: ""
        set(v) = prefs.edit { putString(KEY_CUSTOM, v) }

    var withTimestamps: Boolean
        get() = prefs.getBoolean(KEY_TS, false)
        set(v) = prefs.edit { putBoolean(KEY_TS, v) }

    /** true = pecah otomatis (±6.000 kata per bagian); false = satu bagian. */
    var split: Boolean
        get() = prefs.getBoolean(KEY_SPLIT, true)
        set(v) = prefs.edit { putBoolean(KEY_SPLIT, v) }

    /** Paket aplikasi yang disematkan pengguna sebagai aplikasi AI (untuk yang tidak dikenali otomatis). */
    var pinnedApps: Set<String>
        get() = prefs.getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit { putStringSet(KEY_PINNED, v) }

    var noticeSeen: Boolean
        get() = prefs.getBoolean(KEY_NOTICE, false)
        set(v) = prefs.edit { putBoolean(KEY_NOTICE, v) }

    private companion object {
        const val KEY_TEMPLATE = "template"
        const val KEY_CUSTOM = "custom_prompt"
        const val KEY_TS = "timestamps"
        const val KEY_SPLIT = "split"
        const val KEY_NOTICE = "notice_seen"
        const val KEY_PINNED = "pinned_apps"
    }
}
