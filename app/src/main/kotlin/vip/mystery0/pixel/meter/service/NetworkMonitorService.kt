package vip.mystery0.pixel.meter.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import vip.mystery0.pixel.meter.data.repository.NetworkRepository
import vip.mystery0.pixel.meter.data.source.NetSpeedData
import vip.mystery0.pixel.meter.ui.overlay.OverlayWindow

class NetworkMonitorService : Service() {
    companion object {
        private const val TAG = "NetworkMonitorService"
    }

    private val repository: NetworkRepository by inject()
    private val notificationHelper: NotificationHelper by inject()
    private val notificationManager: NotificationManager by inject()
    private val overlayWindow: OverlayWindow by inject()

    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastNotificationSignature: NotificationSignature? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotif = notificationHelper.buildNotification(
            speed = NetSpeedData(0, 0),
            isLiveUpdate = false,
            isNotificationEnabled = true,
            textUp = "▲ ",
            textDown = "▼ ",
            upFirst = true,
            displayMode = 0,
            textSize = 0.65f,
            unitSize = 0.35f
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: start foreground error", e)
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoring()
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        serviceJob?.cancel()

        // Start Repository Monitoring
        repository.startMonitoring()

        serviceJob = scope.launch {
            // Monitor feature states to auto-stop service when not needed
            launch {
                combine(
                    repository.isOverlayEnabled,
                    repository.isNotificationEnabled
                ) { overlayEnabled, notificationEnabled ->
                    overlayEnabled to notificationEnabled
                }.collect { (overlayEnabled, notificationEnabled) ->
                    if (!overlayEnabled && !notificationEnabled) {
                        Log.d(TAG, "Both overlay and notification disabled, stopping service")
                        stopSelf()
                    }
                }
            }

            repository.netSpeed.collect { speed ->
                // Overlay logic
                withContext(Dispatchers.Main) {
                    try {
                        if (repository.isOverlayEnabled.value) {
                            if (Settings.canDrawOverlays(this@NetworkMonitorService)) {
                                overlayWindow.show()
                                overlayWindow.update(speed)
                            } else {
                                Log.w(
                                    TAG,
                                    "overlay enabled but permission not granted, hiding overlay."
                                )
                                overlayWindow.hide()
                            }
                        } else {
                            overlayWindow.hide()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "startMonitoring: overlay window error", e)
                    }
                }

                // Notification logic
                withContext(Dispatchers.Default) {
                    val isNotificationEnabled = repository.isNotificationEnabled.value
                    if (!isNotificationEnabled) {
                        // Reset signature so next enable rebuilds immediately
                        lastNotificationSignature = null
                        return@withContext
                    }

                    val isLiveUpdate = repository.isLiveUpdateEnabled.value
                    val textUp = repository.notificationTextUp.value
                    val textDown = repository.notificationTextDown.value
                    val upFirst = repository.notificationOrderUpFirst.value
                    val displayMode = repository.notificationDisplayMode.value
                    val textSize = repository.notificationTextSize.value
                    val unitSize = repository.notificationUnitSize.value
                    val hideFromDrawer = repository.isHideNotificationDrawer.value

                    val signature = NotificationSignature(
                        speed = speed,
                        isLiveUpdate = isLiveUpdate,
                        textUp = textUp,
                        textDown = textDown,
                        upFirst = upFirst,
                        displayMode = displayMode,
                        textSize = textSize,
                        unitSize = unitSize,
                        hideFromDrawer = hideFromDrawer
                    )

                    if (signature != lastNotificationSignature) {
                        val notification = notificationHelper.buildNotification(
                            speed, isLiveUpdate, isNotificationEnabled,
                            textUp, textDown, upFirst, displayMode,
                            textSize, unitSize, hideFromDrawer
                        )
                        notificationManager.notify(
                            NotificationHelper.NOTIFICATION_ID,
                            notification
                        )
                        lastNotificationSignature = signature
                    }
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF: stopping monitoring immediately to save power")
                    repository.stopMonitoring()
                    if (!repository.isOverlayEnabled.value && !repository.isNotificationEnabled.value) {
                        Log.d(TAG, "Screen OFF: no features enabled, stopping service")
                        stopSelf()
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON: resuming monitoring")
                    if (!repository.isMonitoring.value) {
                        startMonitoring()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        serviceJob?.cancel()
        overlayWindow.hide()
        repository.stopMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private data class NotificationSignature(
        val speed: NetSpeedData,
        val isLiveUpdate: Boolean,
        val textUp: String,
        val textDown: String,
        val upFirst: Boolean,
        val displayMode: Int,
        val textSize: Float,
        val unitSize: Float,
        val hideFromDrawer: Boolean
    )
}
