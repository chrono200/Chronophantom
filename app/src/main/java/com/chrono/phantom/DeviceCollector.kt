package com.chrono.phantom

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Process
import java.util.concurrent.TimeUnit

data class DeviceSnapshot(
    val mobile24: Long = 0L,
    val mobilePrevious24: Long = 0L,
    val wifi24: Long = 0L,
    val batteryPercent: Int = -1,
    val connection: String = "Unknown",
    val topApps: List<Pair<String, String>> = emptyList()
) {
    val trend: String get() = when {
        mobilePrevious24 <= 0L && mobile24 > 0L -> "Usage detected. A previous-day baseline is not available yet."
        mobile24 > mobilePrevious24 * 1.5 -> "Mobile data use is more than 50% higher than the previous 24-hour period."
        mobile24 > mobilePrevious24 -> "Mobile data use is higher than the previous 24-hour period."
        else -> "No increase detected against the previous 24-hour period."
    }
    fun toPromptContext(): String = """
        Device telemetry (Android system-reported aggregates):
        Mobile data last 24h: ${mobile24.formatBytes()}
        Mobile data previous 24h: ${mobilePrevious24.formatBytes()}
        Wi-Fi data last 24h: ${wifi24.formatBytes()}
        Battery level: $batteryPercent%
        Current connection: $connection
        Recent top app foreground usage: ${topApps.joinToString { "${it.first}: ${it.second}" }.ifBlank { "Not available" }}
        Trend assessment: $trend
        Limitations: network data is aggregated for the queried windows; Android may delay accounting. App foreground time is not app data usage.
    """.trimIndent()
}

class DeviceCollector(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot(): DeviceSnapshot {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val network = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        fun usage(type: Int, from: Long, to: Long): Long = try {
            network.querySummaryForDevice(type, null, from, to)?.let { it.rxBytes + it.txBytes } ?: 0L
        } catch (_: Exception) { 0L }
        val mobileNow = usage(ConnectivityManager.TYPE_MOBILE, now - day, now)
        val mobileBefore = usage(ConnectivityManager.TYPE_MOBILE, now - 2 * day, now - day)
        val wifiNow = usage(ConnectivityManager.TYPE_WIFI, now - day, now)
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val connection = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        return DeviceSnapshot(mobileNow, mobileBefore, wifiNow, level, connection, recentApps())
    }

    private fun recentApps(): List<Pair<String, String>> {
        if (!hasUsageAccess()) return emptyList()
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1), System.currentTimeMillis())
            stats.orEmpty().filter { it.totalTimeInForeground > 0 }.sortedByDescending { it.totalTimeInForeground }.take(5).map {
                val label = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it.packageName, 0)).toString() } catch (_: Exception) { it.packageName }
                val minutes = TimeUnit.MILLISECONDS.toMinutes(it.totalTimeInForeground)
                label to "${minutes}m"
            }
        } catch (_: Exception) { emptyList() }
    }
}

fun Long.formatBytes(): String = when {
    this >= 1024L * 1024 * 1024 -> "%.2f GB".format(this / (1024.0 * 1024 * 1024))
    this >= 1024L * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    this >= 1024L -> "%.0f KB".format(this / 1024.0)
    else -> "$this B"
}
