package com.myselavu.app

import android.app.Application
import android.content.Context
import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock

/**
 * Simple manual dependency injection: one container, created once, holding the app's
 * long-lived objects. Screens get what they need from here (no Hilt/Dagger).
 */
class AppContainer(context: Context) {
    val clock = AppClock()
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val settings = SettingsRepository(context)
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
