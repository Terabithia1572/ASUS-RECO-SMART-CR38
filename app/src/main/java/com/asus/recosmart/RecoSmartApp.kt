package com.asus.recosmart

import android.app.Application
import com.asus.recosmart.data.repository.DefaultCameraRepository
import com.asus.recosmart.domain.repository.CameraRepository

class RecoSmartApp : Application() {

    lateinit var cameraRepository: CameraRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        cameraRepository = DefaultCameraRepository()
    }

    companion object {
        lateinit var instance: RecoSmartApp
            private set
    }
}
