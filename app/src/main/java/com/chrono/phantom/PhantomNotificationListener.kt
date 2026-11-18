package com.chrono.phantom

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PhantomNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        try {
            EventStore(applicationContext).use {
                it.add(PhantomEvent(
                    timestamp = sbn.postTime,
                    type = "NOTIFICATION_POSTED",
                    source = label,
                    detail = "A notification was posted by this app"
                ))
            }
        } catch (_: Exception) {
            // A journal write failure must never take down Android's notification listener.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        try {
            EventStore(applicationContext).use {
                it.add(PhantomEvent(
                    timestamp = System.currentTimeMillis(),
                    type = "NOTIFICATION_REMOVED",
                    source = label,
                    detail = "A notification was dismissed or removed"
                ))
            }
        } catch (_: Exception) {
            // Ignore storage errors; the service should remain connected.
        }
    }
}
