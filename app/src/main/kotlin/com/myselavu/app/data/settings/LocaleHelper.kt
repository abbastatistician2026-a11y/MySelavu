package com.myselavu.app.data.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * App language (English / Tamil), chosen inside the app. Stored in plain SharedPreferences so it
 * can be read instantly when the screen is created (before anything else loads).
 */
object LocaleHelper {
    private const val PREFS = "locale"
    private const val KEY = "language"
    val SUPPORTED = listOf("en", "ta")

    fun language(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "en"

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Wraps a context so its resources use the chosen language. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(language(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
