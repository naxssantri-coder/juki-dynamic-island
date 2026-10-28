package com.dynisland.app.notification

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.dynisland.app.data.IslandSettings
import com.dynisland.app.domain.IslandAction
import com.dynisland.app.domain.IslandController
import com.dynisland.app.domain.IslandEvent
import com.dynisland.app.domain.IslandEventType
import kotlinx.coroutines.flow.StateFlow

/**
 * Turns posted notifications into island events. Only apps the user allowed are shown, and only
 * when the Notifications feature is on. Incoming calls are recognised from the dialer's
 * notification (category = call), so no phone-state permission is needed.
 */
class NotificationEvents(
    private val context: Context,
    private val controller: IslandController,
    private val settings: StateFlow<IslandSettings>,
) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val seenCalls = HashSet<String>()

    fun onPosted(sbn: StatusBarNotification) {
        val s = settings.value
        if (!s.islandEnabled || !s.notificationsEnabled) return
        if (sbn.packageName == context.packageName) return
        val n = sbn.notification ?: return

        if (isIncomingCall(n)) {
            if (seenCalls.add(sbn.key)) controller.post(callEvent(sbn, n, s))
            return
        }
        if (sbn.packageName !in s.allowedApps) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS) return

        controller.post(messageEvent(sbn, n, s))
    }

    fun onRemoved(sbn: StatusBarNotification) {
        seenCalls.remove(sbn.key)
        controller.remove(idFor(sbn))
    }

    private fun isIncomingCall(n: Notification): Boolean {
        if (n.category != Notification.CATEGORY_CALL) return false
        val callType = n.extras?.getInt("android.callType", 0) ?: 0 // CallStyle.CALL_TYPE_INCOMING == 1
        return callType == 1 || n.fullScreenIntent != null
    }

    private fun callEvent(sbn: StatusBarNotification, n: Notification, s: IslandSettings): IslandEvent {
        val caller = if (canShowContent(s)) {
            n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        } else {
            null
        }
        return IslandEvent(
            id = idFor(sbn),
            type = IslandEventType.Call,
            icon = "📞",
            compactText = caller ?: "Incoming call",
            title = caller ?: "Incoming call",
            subtitle = "Incoming call",
            sticky = true,
            actions = openAction(sbn, n),
        )
    }

    private fun messageEvent(sbn: StatusBarNotification, n: Notification, s: IslandSettings): IslandEvent {
        val appLabel = labelFor(sbn.packageName)
        val extras = n.extras
        val show = canShowContent(s)
        val title = if (show) extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() else null
        val text = if (show) {
            (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        } else {
            null
        }
        val heading = title ?: appLabel
        return IslandEvent(
            id = idFor(sbn),
            type = IslandEventType.Notification,
            icon = "💬",
            compactText = heading,
            title = heading,
            subtitle = text ?: if (show) appLabel else "New notification · $appLabel",
            actions = openAction(sbn, n),
        )
    }

    /** Opening another app's PendingIntent from the background can be refused by Android; failure is ignored. */
    private fun openAction(sbn: StatusBarNotification, n: Notification): List<IslandAction> {
        val pi = n.contentIntent ?: return emptyList()
        return listOf(
            IslandAction("Open") {
                runCatching { pi.send() }
                controller.remove(idFor(sbn))
            },
        )
    }

    private fun canShowContent(s: IslandSettings): Boolean =
        s.showNotificationPreview && keyguard?.isKeyguardLocked != true

    private fun idFor(sbn: StatusBarNotification) = "notif-${sbn.key}"

    @Suppress("DEPRECATION")
    private fun labelFor(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
