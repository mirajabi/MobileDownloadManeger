package com.miaadrajabi.fetch

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * A draggable orb drawn above other apps. Tap or flick up to open a small
 * desk. Flick down, or tap again, to collapse it.
 */
class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: FrameLayout? = null
    private var orb: BubbleOrb? = null
    private var panel: View? = null
    private var expanded = false
    private var pulse: ValueAnimator? = null
    private var snap: ValueAnimator? = null
    private val onQueue: (List<Transfer>) -> Unit = { items -> render(items) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOver(this)) {
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, watchingNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = layoutInflater().inflate(R.layout.bubble_overlay, null) as FrameLayout
        root = view
        orb = view.findViewById(R.id.bubbleOrb)
        panel = view.findViewById(R.id.bubblePanel)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        params.x = prefs.getInt(KEY_X, screenWidth() - dp(80))
        params.y = prefs.getInt(KEY_Y, screenHeight() / 3)
        try {
            windowManager.addView(view, params)
        } catch (error: Exception) {
            stopSelf()
            return
        }
        val orbView = orb ?: return
        orbView.setOnTouchListener(dragListener())
        view.findViewById<View>(R.id.bubblePaste).setOnClickListener { pasteCopied() }
        view.findViewById<View>(R.id.bubbleStart).setOnClickListener { startTyped() }
        (application as FetchApp).store.watch(onQueue)
    }

    override fun onDestroy() {
        pulse?.cancel()
        snap?.cancel()
        val app = application as? FetchApp
        app?.store?.unwatch(onQueue)
        val view = root
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (error: Exception) {
                // The window was already detached.
            }
        }
        root = null
        super.onDestroy()
    }

    private fun render(items: List<Transfer>) {
        val orbView = orb ?: return
        val active = items.firstOrNull {
            it.status == TransferStore.STATUS_RUNNING ||
                it.status == TransferStore.STATUS_QUEUED ||
                it.status == TransferStore.STATUS_RETRYING
        }
        val haul = Haul.snapshot(this)
        if (active != null && active.percent >= 0) {
            orbView.sweep = active.percent * 3.6f
        } else if (active != null) {
            orbView.sweep = 70f
        } else {
            orbView.sweep = 0f
        }
        setPulse(active != null)
        val rank = root?.findViewById<TextView>(R.id.bubbleRank) ?: return
        rank.text = haul.rank
        root?.findViewById<TextView>(R.id.bubbleHaul)?.text =
            "${haul.files} saved · ${Haul.formatBytes(haul.bytes)}"
        root?.findViewById<TextView>(R.id.bubbleToward)?.text = haul.toward
        val label = root?.findViewById<TextView>(R.id.bubbleActive)
        val bar = root?.findViewById<ProgressBar>(R.id.bubbleProgress)
        if (active == null) {
            label?.text = "Nothing moving"
            bar?.progress = 0
            bar?.isIndeterminate = false
        } else {
            val name = if (active.title.isNotBlank()) active.title else active.fileName
            val percent = if (active.percent >= 0) "${active.percent}%  " else ""
            label?.text = percent + name
            bar?.isIndeterminate = active.percent < 0
            if (active.percent >= 0) bar?.progress = active.percent
        }
        if (Haul.takePop(this)) celebrate()
    }

    private fun celebrate() {
        val pop = root?.findViewById<TextView>(R.id.bubblePop) ?: return
        val orbView = orb ?: return
        pop.visibility = View.VISIBLE
        pop.alpha = 1f
        pop.translationY = 0f
        pop.animate().translationY(-dp(28).toFloat()).alpha(0f).setDuration(700).withEndAction {
            pop.visibility = View.GONE
        }.start()
        orbView.animate().scaleX(1.18f).scaleY(1.18f).setDuration(140).withEndAction {
            orbView.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        }.start()
    }

    private fun setPulse(active: Boolean) {
        val orbView = orb ?: return
        if (!active) {
            pulse?.cancel()
            pulse = null
            orbView.scaleX = 1f
            orbView.scaleY = 1f
            return
        }
        if (pulse != null) return
        val animator = ValueAnimator.ofFloat(1f, 1.06f)
        animator.duration = 700
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener {
            val scale = it.animatedValue as Float
            orbView.scaleX = scale
            orbView.scaleY = scale
        }
        animator.start()
        pulse = animator
    }

    private fun pasteCopied() {
        val field = root?.findViewById<EditText>(R.id.bubbleLink) ?: return
        val raw = ClipOffer.rawText(this)
        val links = LinkParser.parse(raw)
        if (links.isEmpty()) {
            Toast.makeText(this, "No http link on the clipboard.", Toast.LENGTH_SHORT).show()
            return
        }
        field.setText(links.joinToString("\n"))
    }

    private fun startTyped() {
        val field = root?.findViewById<EditText>(R.id.bubbleLink) ?: return
        val links = LinkParser.parse(field.text?.toString().orEmpty())
        if (links.isEmpty()) {
            Toast.makeText(this, "Drop an http or https link first.", Toast.LENGTH_SHORT).show()
            return
        }
        DownloadDesk.startPlain(this, links)
        field.setText("")
        Toast.makeText(
            this,
            if (links.size == 1) "Started" else "Started ${links.size}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dragListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startX = 0
            private var startY = 0
            private var dragging = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        snap?.cancel()
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8))) {
                            dragging = true
                        }
                        if (dragging) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            clamp()
                            updateWindow()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dy = event.rawY - downRawY
                        if (!dragging) {
                            setExpanded(!expanded)
                        } else if (dy < -dp(48) && !expanded) {
                            setExpanded(true)
                        } else if (dy > dp(48) && expanded) {
                            setExpanded(false)
                        }
                        snapToEdge()
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun setExpanded(open: Boolean) {
        expanded = open
        panel?.visibility = if (open) View.VISIBLE else View.GONE
        val focus = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (open) {
            params.flags = params.flags and focus.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            panel?.alpha = 0f
            panel?.translationY = dp(8).toFloat()
            panel?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(180)?.start()
        } else {
            params.flags = params.flags or focus
            root?.findViewById<EditText>(R.id.bubbleLink)?.clearFocus()
        }
        clamp()
        updateWindow()
    }

    private fun snapToEdge() {
        val width = root?.width ?: dp(64)
        val target = if (params.x + width / 2 < screenWidth() / 2) dp(12) else screenWidth() - width - dp(12)
        val from = params.x
        snap?.cancel()
        val animator = ValueAnimator.ofInt(from, target)
        animator.duration = 220
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            params.x = it.animatedValue as Int
            clamp()
            updateWindow()
        }
        animator.start()
        snap = animator
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_X, target)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    private fun clamp() {
        val width = root?.width ?: dp(64)
        val height = root?.height ?: dp(64)
        val maxX = (screenWidth() - width).coerceAtLeast(0)
        val maxY = (screenHeight() - height).coerceAtLeast(0)
        if (params.x < 0) params.x = 0
        if (params.y < 0) params.y = 0
        if (params.x > maxX) params.x = maxX
        if (params.y > maxY) params.y = maxY
    }

    private fun updateWindow() {
        val view = root ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (error: Exception) {
            // The window can disappear if the permission is revoked.
        }
    }

    private fun layoutInflater() = android.view.LayoutInflater.from(this)

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    @Suppress("DEPRECATION")
    private fun screenWidth(): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    @Suppress("DEPRECATION")
    private fun screenHeight(): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun watchingNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Floating orb",
                    NotificationManager.IMPORTANCE_MIN
                )
                channel.description = "Keeps the Fetch orb above other apps"
                manager.createNotificationChannel(channel)
            }
        }
        val open = PendingIntent.getActivity(
            this,
            9,
            Intent(this, QueueActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_arrow)
            .setContentTitle("Fetch")
            .setContentText("The orb is on screen")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun immutable(): Int {
        return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        private const val CHANNEL_ID = "fetch_bubble"
        private const val NOTIFICATION_ID = 7104
        private const val PREFS = "fetch_bubble"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun canDrawOver(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 23) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun start(context: Context) {
            if (!canDrawOver(context)) return
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }
}
