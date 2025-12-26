package vip.mystery0.pixelpulse.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vip.mystery0.pixelpulse.data.repository.NetworkRepository
import vip.mystery0.pixelpulse.data.source.NetSpeedData
import vip.mystery0.pixelpulse.service.NetworkMonitorService

class MainViewModel(
    private val application: Application,
    private val repository: NetworkRepository
) : AndroidViewModel(application) {

    private val _currentSpeed = MutableStateFlow(NetSpeedData(0, 0))
    val currentSpeed = _currentSpeed.asStateFlow()

    val isShizukuMode = repository.isShizukuMode
    val shizukuPermissionGranted = repository.shizukuPermissionGranted
    val blacklistedInterfaces = repository.blacklistedInterfaces
    val isOverlayEnabled = repository.isOverlayEnabled
    val hasUsagePermission = repository.hasUsagePermission

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    init {
        startUiPolling()
    }

    private fun startUiPolling() {
        viewModelScope.launch {
            while (true) {
                _currentSpeed.value = repository.getCurrentSpeed()
                delay(1000)
            }
        }
    }

    private val _serviceStartError = MutableStateFlow<Pair<String, String>?>(null)
    val serviceStartError = _serviceStartError.asStateFlow()

    fun startService() {
        _serviceStartError.value = null

        // 1. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    application,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                _serviceStartError.value =
                    "Notification permission required" to android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                return
            }
        }

        // 2. Check Overlay Permission if enabled
        if (isOverlayEnabled.value) {
            if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(
                    application
                )
            ) {
                _serviceStartError.value =
                    "Overlay permission required for Floating Window" to android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                return
            }
        }

        val intent = Intent(application, NetworkMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
            _isServiceRunning.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _serviceStartError.value =
                "Failed to start service: ${e.message}" to android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            _isServiceRunning.value = false
        }
    }

    fun stopService() {
        val intent = Intent(application, NetworkMonitorService::class.java)
        application.stopService(intent)
        _isServiceRunning.value = false
        _serviceStartError.value = null
    }

    fun clearError() {
        _serviceStartError.value = null
    }

    fun setShizukuMode(enable: Boolean) {
        repository.setShizukuMode(enable)
    }

    fun setOverlayEnabled(enable: Boolean) {
        repository.setOverlayEnabled(enable)
    }

    fun checkUsagePermission() { // Added
        repository.checkUsagePermission()
    }

    fun requestShizukuPermission() {
        repository.requestShizukuPermission()
    }

    fun addToBlacklist(iface: String) {
        val current = blacklistedInterfaces.value.toMutableSet()
        current.add(iface)
        repository.updateBlacklist(current)
    }

    fun removeFromBlacklist(iface: String) {
        val current = blacklistedInterfaces.value.toMutableSet()
        current.remove(iface)
        repository.updateBlacklist(current)
    }
}
