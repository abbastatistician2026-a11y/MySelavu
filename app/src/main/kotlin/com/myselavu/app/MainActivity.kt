package com.myselavu.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myselavu.app.data.settings.LocaleHelper
import com.myselavu.app.ui.MySelavuRoot

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Apply the language chosen inside the app (English / Tamil).
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MySelavuRoot() }
    }
}
