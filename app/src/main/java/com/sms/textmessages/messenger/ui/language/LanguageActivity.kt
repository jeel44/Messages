package com.sms.textmessages.messenger.ui.language

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.sms.textmessages.messenger.App

class LanguageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // 🚫 Still onboarding - keep AppOpen suppressed until Home is reached
        App.disableAppOpenAd = true

        setContent {
            LanguageScreen()
        }
    }
}