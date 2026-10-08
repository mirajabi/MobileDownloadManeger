package com.miaadrajabi.fetch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Stays in the foreground so a copied link can be offered without the user
 * opening Fetch first. Android still hides clipboard text from a background
 * app, so the offer is a notification until Fetch is in front.
 */
class ClipWatchService : Service() {

    private var clipboard: ClipboardManager? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipChanged()
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, watchingNotification())
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard = manager
        manager.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, watchingNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onClipChanged() {
        if (!EngineSettings.load(this).watchClipboard) {
            stopWatching(this)
            return
        }
        val raw = ClipOffer.rawText(this)
        val links = LinkParser.parse(raw)
        if (links.isEmpty() || !ClipOffer.isNew(this, raw)) return
        ClipOffer.remember(this, raw)
        if (FetchForeground.open) {
            try {
                startActivity(ClipOffer.offerIntent(this, links))
            } catch (error: Exception) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(OFFER_ID, offerNotice(links))
            }
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(OFFER_ID, offerNotice(links))
    }

    private fun offerNotice(links: List<String>): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, OFFER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arrow)
            .setContentTitle(if (links.size == 1) "Start this download?" else "Start these downloads?")
            .setContentText(links.joinToString(", ") { LinkParser.fileNameFrom(it) })
            .setContentIntent(ClipOffer.contentIntent(this, links))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun watchingNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, QueueActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_arrow)
            .setContentTitle("Fetch")
            .setContentText("Watching for a copied link")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Copied links",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Stays up while Fetch is allowed to offer a copied link"
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(OFFER_CHANNEL) == null) {
            val offer = NotificationChannel(
                OFFER_CHANNEL,
                "Link offers",
                NotificationManager.IMPORTANCE_HIGH
            )
            offer.description = "Asks before a copied link starts"
            manager.createNotificationChannel(offer)
        }
    }

    private fun immutable(): Int {
        return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        private const val CHANNEL_ID = "fetch_clip"
        private const val OFFER_CHANNEL = "fetch_clip_offer"
        private const val NOTIFICATION_ID = 7102
        private const val OFFER_ID = 7103

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ClipWatchService::class.java)
            )
        }

        fun stopWatching(context: Context) {
            context.stopService(Intent(context, ClipWatchService::class.java))
        }
    }
}
