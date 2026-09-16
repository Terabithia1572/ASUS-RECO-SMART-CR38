package com.asus.recosmart.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isVehicleModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_VEHICLE_MODE, false))
    val isVehicleModeEnabled: StateFlow<Boolean> = _isVehicleModeEnabled.asStateFlow()

    private val _autoStartRecordingIfIdle = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START_RECORDING, false))
    val autoStartRecordingIfIdle: StateFlow<Boolean> = _autoStartRecordingIfIdle.asStateFlow()

    private val _autoOpenLiveView = MutableStateFlow(prefs.getBoolean(KEY_AUTO_OPEN_LIVE_VIEW, false))
    val autoOpenLiveView: StateFlow<Boolean> = _autoOpenLiveView.asStateFlow()

    private val _preferredMediaFilter = MutableStateFlow(prefs.getString(KEY_PREFERRED_MEDIA_FILTER, "ALL") ?: "ALL")
    val preferredMediaFilter: StateFlow<String> = _preferredMediaFilter.asStateFlow()

    fun setVehicleModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VEHICLE_MODE, enabled).apply()
        _isVehicleModeEnabled.value = enabled
    }

    fun setAutoStartRecordingIfIdle(autoStart: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_RECORDING, autoStart).apply()
        _autoStartRecordingIfIdle.value = autoStart
    }

    fun setAutoOpenLiveView(autoOpen: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_LIVE_VIEW, autoOpen).apply()
        _autoOpenLiveView.value = autoOpen
    }

    fun setPreferredMediaFilter(filter: String) {
        prefs.edit().putString(KEY_PREFERRED_MEDIA_FILTER, filter).apply()
        _preferredMediaFilter.value = filter
    }

    companion object {
        private const val PREFS_NAME = "reco_smart_user_prefs"
        private const val KEY_VEHICLE_MODE = "key_vehicle_mode_enabled"
        private const val KEY_AUTO_START_RECORDING = "key_auto_start_recording_if_idle"
        private const val KEY_AUTO_OPEN_LIVE_VIEW = "key_auto_open_live_view"
        private const val KEY_PREFERRED_MEDIA_FILTER = "key_preferred_media_filter"

        @Volatile
        private var instance: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
