package com.myselavu.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.budget.UnspentMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App settings with friendly defaults, so nothing needs configuring before first use. */
data class AppSettings(
    val setupDone: Boolean = false,
    val profileName: String = "",
    val language: String = "en",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val budgetStartDay: Int = 1,
    val unspentMode: UnspentMode = UnspentMode.MOVE_TO_SAVINGS,
    val lastPaymentMethod: PaymentMethod = PaymentMethod.DEFAULT,
    val dailySummaryEnabled: Boolean = true,
    /** Minutes after midnight; default 9:00 PM. */
    val dailySummaryMinutes: Int = 21 * 60,
    val billRemindersEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val lockAfterSeconds: Int = 60,
    val hideAmounts: Boolean = false,
    /** Debug builds only: days added to the real date. */
    val timeTravelDays: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val setupDone = booleanPreferencesKey("setup_done")
        val profileName = stringPreferencesKey("profile_name")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val startDay = intPreferencesKey("budget_start_day")
        val unspentMode = stringPreferencesKey("unspent_mode")
        val lastMethod = stringPreferencesKey("last_payment_method")
        val dailySummary = booleanPreferencesKey("daily_summary")
        val dailySummaryMinutes = intPreferencesKey("daily_summary_minutes")
        val billReminders = booleanPreferencesKey("bill_reminders")
        val appLock = booleanPreferencesKey("app_lock")
        val lockAfter = intPreferencesKey("lock_after_seconds")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val timeTravel = longPreferencesKey("time_travel_days")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            setupDone = p[Keys.setupDone] ?: d.setupDone,
            profileName = p[Keys.profileName] ?: d.profileName,
            language = p[Keys.language] ?: d.language,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            budgetStartDay = (p[Keys.startDay] ?: d.budgetStartDay).coerceIn(1, 28),
            unspentMode = p[Keys.unspentMode]?.let { runCatching { UnspentMode.valueOf(it) }.getOrNull() } ?: d.unspentMode,
            lastPaymentMethod = p[Keys.lastMethod]?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() } ?: d.lastPaymentMethod,
            dailySummaryEnabled = p[Keys.dailySummary] ?: d.dailySummaryEnabled,
            dailySummaryMinutes = p[Keys.dailySummaryMinutes] ?: d.dailySummaryMinutes,
            billRemindersEnabled = p[Keys.billReminders] ?: d.billRemindersEnabled,
            appLockEnabled = p[Keys.appLock] ?: d.appLockEnabled,
            lockAfterSeconds = p[Keys.lockAfter] ?: d.lockAfterSeconds,
            hideAmounts = p[Keys.hideAmounts] ?: d.hideAmounts,
            timeTravelDays = p[Keys.timeTravel] ?: d.timeTravelDays,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSetupDone(done: Boolean) = context.dataStore.edit { it[Keys.setupDone] = done }
    suspend fun setProfileName(name: String) = context.dataStore.edit { it[Keys.profileName] = name.trim() }
    suspend fun setLanguage(tag: String) = context.dataStore.edit { it[Keys.language] = tag }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setBudgetStartDay(day: Int) = context.dataStore.edit { it[Keys.startDay] = day.coerceIn(1, 28) }
    suspend fun setUnspentMode(mode: UnspentMode) = context.dataStore.edit { it[Keys.unspentMode] = mode.name }
    suspend fun setLastPaymentMethod(m: PaymentMethod) = context.dataStore.edit { it[Keys.lastMethod] = m.name }
    suspend fun setDailySummary(enabled: Boolean, minutes: Int) = context.dataStore.edit {
        it[Keys.dailySummary] = enabled
        it[Keys.dailySummaryMinutes] = minutes
    }
    suspend fun setBillReminders(enabled: Boolean) = context.dataStore.edit { it[Keys.billReminders] = enabled }
    suspend fun setAppLock(enabled: Boolean, lockAfterSeconds: Int) = context.dataStore.edit {
        it[Keys.appLock] = enabled
        it[Keys.lockAfter] = lockAfterSeconds
    }
    suspend fun setHideAmounts(hide: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = hide }
    suspend fun setTimeTravelDays(days: Long) = context.dataStore.edit { it[Keys.timeTravel] = days }
}
