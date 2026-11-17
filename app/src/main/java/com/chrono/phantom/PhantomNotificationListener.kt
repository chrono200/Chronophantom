package com.chrono.phantom

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PhantomNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        EventStore(this).use {
            it.add(PhantomEvent(
                timestamp = sbn.postTime,
                type = "NOTIFICATION_POSTED",
                source = label,
                detail = "A notification was posted by this app"
            ))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        EventStore(this).use {
            it.add(PhantomEvent(
                timestamp = System.currentTimeMillis(),
                type = "NOTIFICATION_REMOVED",
                source = label,
                detail = "A notification was dismissed or removed"
            ))
        }
    }
}
