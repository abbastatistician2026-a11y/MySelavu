package com.myselavu.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myselavu.app.data.settings.AppSettings
import com.myselavu.app.ui.budget.CategoriesScreen
import com.myselavu.app.ui.budget.SalaryScreen
import com.myselavu.app.ui.expense.AddExpenseContent
import com.myselavu.app.ui.history.HistoryScreen
import com.myselavu.app.ui.home.DashboardActions
import com.myselavu.app.ui.home.DashboardScreen
import com.myselavu.app.ui.more.SettingsScreen
import com.myselavu.app.ui.setup.SetupWizard
import com.myselavu.app.ui.summary.MonthSummaryScreen
import com.myselavu.app.ui.summary.YearSummaryScreen
import com.myselavu.app.ui.theme.MySelavuTheme
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private val s = SampleScreens
    private val snackbar = SnackbarHostState()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { MySelavuTheme { content() } }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Composable private fun Dashboard(hide: Boolean = false) =
        DashboardScreen(s.dashboard(hide), s.templates, snackbar, DashboardActions())

    @Test @Config(qualifiers = "+en-notnight") fun home_en_light() = shot("01_home_en_light") { Dashboard() }
    @Test @Config(qualifiers = "+en-night") fun home_en_dark() = shot("02_home_en_dark") { Dashboard() }
    @Test @Config(qualifiers = "+ta-notnight") fun home_ta_light() = shot("03_home_ta_light") { Dashboard() }
    @Test @Config(qualifiers = "+ta-night") fun home_ta_dark() = shot("04_home_ta_dark") { Dashboard() }
    @Test @Config(qualifiers = "+en-notnight") fun home_hidden() = shot("05_home_en_hidden_amounts") { Dashboard(hide = true) }
    @Test @Config(qualifiers = "+en-notnight", fontScale = 1.5f) fun home_large_font() = shot("06_home_en_large_font") { Dashboard() }

    @Composable private fun AddSheet() = Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        AddExpenseContent(
            existing = null, presetCategoryId = null, categories = s.categories, rankedIds = listOf(1, 2, 3, 4),
            templates = s.templates, lastMethod = PaymentMethod.UPI, today = s.today,
            onSave = { _, _, _ -> }, onTemplate = {}, onDelete = {}, onDuplicate = {},
        )
    }

    @Test @Config(qualifiers = "+en-notnight") fun add_en_light() = shot("08_add_expense_en_light") { AddSheet() }
    @Test @Config(qualifiers = "+ta-night") fun add_ta_dark() = shot("09_add_expense_ta_dark") { AddSheet() }

    @Composable private fun History() = HistoryScreen(
        s.history(), s.categories, false, snackbar, {}, {}, { _, _ -> }, {}, {}, {}, {}, {},
    )

    @Test @Config(qualifiers = "+en-notnight") fun history_en_light() = shot("10_history_en_light") { History() }
    @Test @Config(qualifiers = "+ta-night") fun history_ta_dark() = shot("11_history_ta_dark") { History() }

    @Test @Config(qualifiers = "+en-notnight") fun months_en_light() =
        shot("12_month_summary_en_light") { MonthSummaryScreen(s.months, s.byId, Money.rupees(33950L), false, snackbar) {} }

    @Test @Config(qualifiers = "+en-night") fun years_en_dark() =
        shot("13_year_summary_en_dark") { YearSummaryScreen(s.months, s.byId, false, snackbar) {} }

    @Test @Config(qualifiers = "+en-notnight") fun categories_en_light() = shot("14_categories_en_light") {
        val d = s.dashboard()
        CategoriesScreen(d.cards, d.unplanned, false, snackbar, {}, {}, { _, _, _ -> }, {}, { _, _ -> }, { _, _, _ -> })
    }

    @Test @Config(qualifiers = "+en-notnight") fun salary_en_light() = shot("15_salary_en_light") {
        SalaryScreen(Money.rupees(55000L), emptyList(), emptyList(), s.today, false, snackbar, {}, { _, _ -> }, { _, _, _ -> }, {})
    }

    @Test @Config(qualifiers = "+en-notnight") fun settings_en_light() = shot("16_settings_en_light") {
        SettingsScreen(AppSettings(setupDone = true, profileName = "Abbas"), "en", snackbar, {}, {}, {}, {}, {}, {}, {}, null)
    }

    @Test @Config(qualifiers = "+en-notnight") fun setup_en_light() = shot("17_setup_welcome_en_light") {
        SetupWizard("en", {}) { _, _, _, _, _ -> }
    }

    @Test @Config(qualifiers = "+ta-notnight") fun setup_ta_light() = shot("18_setup_welcome_ta_light") {
        SetupWizard("ta", {}) { _, _, _, _, _ -> }
    }

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
                Image(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.size(112.dp),
                    colorFilter = ColorFilter.tint(Color(0xFF0E6B68)))
            }
        }
    }
}
