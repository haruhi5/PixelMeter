package vip.mystery0.pixelpulse.data.repository

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import rikka.shizuku.Shizuku
import vip.mystery0.pixelpulse.data.source.NetSpeedData
import vip.mystery0.pixelpulse.data.source.impl.ShizukuSpeedDataSource
import vip.mystery0.pixelpulse.data.source.impl.StandardSpeedDataSource

class NetworkRepository(
    private val context: Context,
    private val standardDataSource: StandardSpeedDataSource,
    private val shizukuDataSource: ShizukuSpeedDataSource
) : KoinComponent {
    private val _isShizukuMode = MutableStateFlow(false)
    val isShizukuMode: StateFlow<Boolean> = _isShizukuMode.asStateFlow()

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = _shizukuPermissionGranted.asStateFlow()

    private val _blacklistedInterfaces = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedInterfaces: StateFlow<Set<String>> = _blacklistedInterfaces.asStateFlow()

    private val _isOverlayEnabled = MutableStateFlow(false)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _shizukuPermissionGranted.tryEmit(false)
        checkShizukuPermission()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _shizukuPermissionGranted.tryEmit(true)
            }
        }

    init {
        try {
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            checkShizukuPermission()
        } catch (_: Throwable) {
            // Shizuku not installed or not running
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
            } else {
                _shizukuPermissionGranted.tryEmit(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                _shizukuPermissionGranted.tryEmit(granted)
            } else {
                _shizukuPermissionGranted.tryEmit(false)
            }
        } catch (_: Throwable) {
            _shizukuPermissionGranted.tryEmit(false)
        }
    }

    fun setShizukuMode(enable: Boolean) {
        _isShizukuMode.value = enable
        if (enable) {
            checkShizukuPermission()
        }
    }

    fun checkUsagePermission() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        _hasUsagePermission.value = (mode == AppOpsManager.MODE_ALLOWED)
    }

    fun updateBlacklist(list: Set<String>) {
        _blacklistedInterfaces.value = list
        shizukuDataSource.updateBlacklist(list)
    }

    fun setOverlayEnabled(enable: Boolean) {
        _isOverlayEnabled.value = enable
    }

    suspend fun getCurrentSpeed(): NetSpeedData {
        return if (_isShizukuMode.value && _shizukuPermissionGranted.value) {
            try {
                shizukuDataSource.getNetSpeed()
            } catch (e: Exception) {
                // Fallback to standard if Shizuku fails transiently
                standardDataSource.getNetSpeed()
            }
        } else {
            standardDataSource.getNetSpeed()
        }
    }
}
