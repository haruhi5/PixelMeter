package vip.mystery0.pixel.meter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.meter.data.repository.NetworkRepository
import vip.mystery0.pixel.meter.service.NetworkMonitorService

class BootReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val TAG = "BootReceiver"
    }

    private val repository: NetworkRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val isAutoStart = repository.isAutoStartServiceEnabled.value
            if (isAutoStart) {
                Log.i(TAG, "boot completed, starting service")
                val serviceIntent = Intent(context, NetworkMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.i(TAG, "boot completed, but auto-start is disabled")
            }
        }
    }
}
