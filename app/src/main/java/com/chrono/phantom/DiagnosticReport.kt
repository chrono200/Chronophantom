package com.chrono.phantom

object DiagnosticReport {
    fun build(events: List<PhantomEvent>, insights: List<PhantomInsight>, usageAccess: Boolean, notificationAccess: Boolean): String {
        val lines = mutableListOf<String>()
        lines += "CHRONO PHANTOM — DEVICE EVENT INTELLIGENCE"
        lines += "Generated: ${java.util.Date()}"
        lines += "Events included: ${events.size}"
        lines += "Usage Access: ${if (usageAccess) "enabled" else "disabled"}"
        lines += "Notification Access: ${if (notificationAccess) "enabled" else "disabled"}"
        lines += ""
        lines += "INSIGHTS"
        insights.forEach { lines += "[${it.severity}] ${it.title}\n${it.explanation}\nEvidence: ${it.evidence}\n" }
        lines += "EVENT TIMELINE"
        events.sortedByDescending { it.timestamp }.forEach { lines += "${it.displayTime()} | ${it.type} | ${it.source} | ${it.detail}" }
        lines += ""
        lines += "LIMITATIONS: Events depend on Android APIs, granted permissions, device vendor behaviour, and collection opportunities. This report is not a complete record of all device activity."
        return lines.joinToString("\n")
    }
}
