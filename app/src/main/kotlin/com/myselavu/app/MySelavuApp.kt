package com.myselavu.app

import android.app.Application
import android.content.Context
import com.myselavu.app.data.BackupManager
import com.myselavu.app.data.BudgetRepository
import com.myselavu.app.data.DemoData
import com.myselavu.app.data.ExcelExporter
import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock

/**
 * Simple manual dependency injection: one container, created once, holding the app's
 * long-lived objects. Screens get what they need from here (no Hilt/Dagger).
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val clock = AppClock()
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val settings = SettingsRepository(context)
    val repository: BudgetRepository by lazy { BudgetRepository(database, settings, clock) }
    val exporter: ExcelExporter by lazy { ExcelExporter(database, repository) }
    val demoData: DemoData by lazy { DemoData(repository, settings, clock) }
    val backup: BackupManager by lazy { BackupManager(appContext, database, settings, clock) }
}

class MySelavuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MySelavuApp).container
