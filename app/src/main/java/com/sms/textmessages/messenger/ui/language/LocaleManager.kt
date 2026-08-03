package com.sms.textmessages.messenger.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {

    /** Map UI labels from LanguageScreen to BCP-47 language tags. */
    fun toLocaleCode(selection: String): String = when (selection) {
        "English" -> "en"
        "Hindi" -> "hi"
        "China" -> "zh"
        "Spain" -> "es"
        "Saudi Arabia" -> "ar"
        "Germany" -> "de"
        "France" -> "fr"
        "Norway" -> "nb"
        else -> selection.takeIf { it.length in 2..5 } ?: "en"
    }

    fun setLocale(context: Context, languageCode: String): Context {
        val code = toLocaleCode(languageCode)
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
