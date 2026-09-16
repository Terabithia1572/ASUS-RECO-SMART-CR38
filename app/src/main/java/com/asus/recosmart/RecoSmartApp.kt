package com.asus.recosmart

import android.app.Application
import com.asus.recosmart.data.preferences.UserPreferencesManager
import com.asus.recosmart.data.repository.DefaultCameraRepository
import com.asus.recosmart.domain.repository.CameraRepository

class RecoSmartApp : Application() {

    lateinit var cameraRepository: CameraRepository
        private set

    lateinit var userPreferencesManager: UserPreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        cameraRepository = DefaultCameraRepository()
        userPreferencesManager = UserPreferencesManager.getInstance(this)
    }

    companion object {
        lateinit var instance: RecoSmartApp
            private set
    }
}

