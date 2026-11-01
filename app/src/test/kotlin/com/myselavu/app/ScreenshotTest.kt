package com.myselavu.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myselavu.app.ui.preview.PreviewHomeScreen
import com.myselavu.app.ui.theme.MySelavuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Generates the screenshots in docs/screenshots/ (light + dark, English + Tamil).
 * Run by CI with `./gradlew :app:recordRoborazziDebug`; no emulator needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** A fixed "today" so the screenshots never change by accident. */
    private val today = LocalDate.of(2026, 11, 18)

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { MySelavuTheme { content() } }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test @Config(qualifiers = "+en-notnight")
    fun home_en_light() = shot("01_home_en_light") { PreviewHomeScreen(today) }

    @Test @Config(qualifiers = "+en-night")
    fun home_en_dark() = shot("02_home_en_dark") { PreviewHomeScreen(today) }

    @Test @Config(qualifiers = "+ta-notnight")
    fun home_ta_light() = shot("03_home_ta_light") { PreviewHomeScreen(today) }

    @Test @Config(qualifiers = "+ta-night")
    fun home_ta_dark() = shot("04_home_ta_dark") { PreviewHomeScreen(today) }

    @Test @Config(qualifiers = "+en-notnight")
    fun home_en_hidden_amounts() = shot("05_home_en_hidden_amounts") { PreviewHomeScreen(today, hideAmounts = true) }

    @Test @Config(qualifiers = "+en-notnight", fontScale = 1.5f)
    fun home_en_large_font() = shot("06_home_en_large_font") { PreviewHomeScreen(today) }

    @Test @Config(qualifiers = "+en-notnight")
    fun launcher_icon() = shot("07_launcher_icons") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (shape in listOf(CircleShape, RoundedCornerShape(28.dp))) {
                Box(Modifier.size(112.dp).clip(shape)) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(112.dp))
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(112.dp))
                }
            }
            Box(Modifier.size(112.dp).clip(CircleShape)) {
                Box(Modifier.size(112.dp).clip(CircleShape)) {
                    Image(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.size(112.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF0E6B68)))
                }
            }
        }
    }
}
