package com.dynisland.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynisland.app.IslandApp
import com.dynisland.app.overlay.IslandOverlayService
import com.dynisland.app.overlay.OverlayPermission

/**
 * Bound by the system only after the user enables Notification access. It does no work of its
 * own: it forwards callbacks to [NotificationEvents] and doubles as the permission anchor for
 * MediaSessionManager.
 */
class IslandNotificationListener : NotificationListenerService() {
    private val app get() = application as IslandApp

    override fun onListenerConnected() {
        super.onListenerConnected()
        // After a reboot the system binds us automatically; use that to bring the island back.
        // May be refused by background-start rules on some devices; that is caught inside start().
        if (app.settings.value.islandEnabled && OverlayPermission.isGranted(this)) {
            IslandOverlayService.start(this)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) app.notificationEvents.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) app.notificationEvents.onRemoved(sbn)
    }
}
