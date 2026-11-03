package com.nossam.smahobutton

import android.app.Application
import com.nossam.smahobutton.data.SettingsRepository
import com.nossam.smahobutton.runtime.SmahoRuntime
import com.nossam.smahobutton.service.ensureChannel

class SmahoApp : Application() {
    lateinit var repository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        ensureChannel(this)
        SmahoRuntime.install(this, repository)
    }
}
