package com.myselavu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myselavu.app.ui.preview.PreviewHomeScreen
import com.myselavu.app.ui.theme.MySelavuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val today = appContainer.clock.today()
        setContent {
            MySelavuTheme {
                PreviewHomeScreen(today = today)
            }
        }
    }
}
