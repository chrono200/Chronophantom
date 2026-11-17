package com.chrono.phantom

data class PhantomEvent(
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val source: String,
    val detail: String,
    val severity: String = "INFO"
) {
    fun displayTime(): String = java.text.SimpleDateFormat("dd MMM, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}
