package com.chrono.phantom

data class PhantomInsight(val title: String, val explanation: String, val severity: String, val evidence: String)

object InsightEngine {
    fun analyse(events: List<PhantomEvent>): List<PhantomInsight> {
        if (events.isEmpty()) return listOf(PhantomInsight("Not enough history yet", "Sync events and allow the journal to collect activity. Insights become more useful as the timeline grows.", "INFO", "No events in the selected window"))
        val ascending = events.sortedBy { it.timestamp }
        val results = mutableListOf<PhantomInsight>()
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        val recent = ascending.filter { it.timestamp >= hourAgo }

        val notifGroups = recent.filter { it.type == "NOTIFICATION_POSTED" }.groupBy { it.source }
        notifGroups.filterValues { it.size >= 8 }.forEach { (app, list) ->
            results += PhantomInsight("Notification burst: $app", "This app posted ${list.size} notifications in the last hour. Review its notification categories and background behaviour.", "WARNING", list.last().displayTime())
        }

        val switches = recent.filter { it.type == "APP_FOREGROUND" }
        val rapid = switches.zipWithNext().count { (a,b) -> b.timestamp - a.timestamp in 1..30_000 }
        if (rapid >= 12) results += PhantomInsight("Rapid app switching", "There were $rapid foreground transitions separated by 30 seconds or less. This may be normal usage, but it can help explain repeated background/foreground activity.", "INFO", "${switches.size} app foreground events in the last hour")

        val connections = recent.filter { it.type == "CONNECTIVITY" }
        if (connections.size >= 5) results += PhantomInsight("Unstable connectivity", "The device recorded ${connections.size} connection changes in the last hour. Repeated reconnects can affect battery and mobile data use.", "WARNING", connections.last().detail)

        val battery = ascending.filter { it.type == "BATTERY_DROP" }
        if (battery.isNotEmpty()) results += PhantomInsight("Battery level changes observed", "A battery decrease of at least three percentage points was observed between two collection samples. The timeline can show what else was happening around that sample.", "INFO", battery.takeLast(3).joinToString { it.detail })

        if (results.isEmpty()) results += PhantomInsight("No strong anomaly detected", "The current event window has no patterns that cross PHANTOM's local alert thresholds. This is not proof that every process is behaving normally.", "INFO", "${events.size} events examined")
        return results
    }
}
