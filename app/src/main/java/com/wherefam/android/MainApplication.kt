package com.wherefam.android

import android.app.Application
import com.wherefam.android.di.appModule
import com.wherefam.android.di.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MainApplication)
            modules(appModule, viewModel)
        }
    }
}