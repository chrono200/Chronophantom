package com.chrono.phantom

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.provider.Settings
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DeviceObserver(private val context: Context, private val store: EventStore) {
    private val prefs = context.getSharedPreferences("phantom_observer", Context.MODE_PRIVATE)

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(":").any { it.contains(context.packageName) }
    }

    fun sync(): Int {
        var added = 0
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_usage_sync", now - 24 * 60 * 60 * 1000L)
        if (hasUsageAccess()) {
            try {
                val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = manager.queryEvents(last.coerceAtMost(now - 1000L), now)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val foreground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (android.os.Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                    if (foreground && event.packageName != context.packageName) {
                        val app = appName(event.packageName)
                        if (store.add(PhantomEvent(timestamp = event.timeStamp, type = "APP_FOREGROUND", source = app, detail = "App entered foreground"))) added++
                    }
                }
            } catch (_: Exception) { }
        }
        prefs.edit().putLong("last_usage_sync", now).apply()

        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            if (percent >= 0) {
                val previous = prefs.getInt("last_battery", -1)
                if (previous >= 0 && previous - percent >= 3) {
                    if (store.add(PhantomEvent(timestamp = now, type = "BATTERY_DROP", source = "Battery", detail = "Battery fell from $previous% to $percent%", severity = "WARNING"))) added++
                }
                prefs.edit().putInt("last_battery", percent).apply()
            }
        } catch (_: Exception) { }

        // Network state is optional: never let a restricted device or OEM ROM crash the app.
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val network = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
            val previousNetwork = prefs.getString("last_network", null)
            if (previousNetwork != null && previousNetwork != network) {
                if (store.add(PhantomEvent(timestamp = now, type = "CONNECTIVITY", source = "Network", detail = "Connection changed: $previousNetwork → $network"))) added++
            }
            prefs.edit().putString("last_network", network).apply()
        } catch (_: SecurityException) {
            // ACCESS_NETWORK_STATE may be unavailable on older or modified Android builds.
        } catch (_: Exception) { }

        if (store.count() == 0) store.add(PhantomEvent(timestamp = now, type = "SYSTEM", source = "ChronoPhantom", detail = "Local event journal initialized"))
        return added
    }

    private fun appName(packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) { packageName }
}