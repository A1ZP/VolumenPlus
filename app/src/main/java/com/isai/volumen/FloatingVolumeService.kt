package com.isai.volumen

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Servicio en primer plano que dibuja un control flotante ＋ / － sobre cualquier app.
 * - Toque: sube/baja un paso.  - Mantener: repite.  - Arrastrar: mueve y se pega al borde.
 * También deja controles en la notificación (sirven aunque no haya permiso de superposición).
 */
class FloatingVolumeService : Service() {

    companion object {
        private const val ACTION_UP = "com.isai.volumen.UP"
        private const val ACTION_DOWN = "com.isai.volumen.DOWN"
        private const val ACTION_STOP = "com.isai.volumen.STOP"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIF_ID = 1

        private const val REPEAT_DELAY_MS = 420L
        private const val REPEAT_INTERVAL_MS = 120L
        private const val FADE_AFTER_MS = 3000L
        private const val IDLE_ALPHA = 0.45f

        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, FloatingVolumeService::class.java))

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, FloatingVolumeService::class.java))
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var bubble: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fade = Runnable { bubble?.animate()?.alpha(IDLE_ALPHA)?.setDuration(400)?.start() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startInForeground()
        if (Settings.canDrawOverlays(this)) addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UP -> VolumeController.adjust(this, true, Prefs.bubbleStream(this))
            ACTION_DOWN -> VolumeController.adjust(this, false, Prefs.bubbleStream(this))
            ACTION_STOP -> {
                Prefs.setBubbleEnabled(this, false)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Al girar la pantalla, vuelve a pegarlo a un borde válido.
        bubble?.post { snapToEdge(animate = false) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bubble?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    // ---------- Notificación ----------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vol_up)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_minus, getString(R.string.btn_down), ACTION_DOWN, 1))
            .addAction(action(R.drawable.ic_plus, getString(R.string.btn_up), ACTION_UP, 2))
            .addAction(action(R.drawable.ic_vol_down, getString(R.string.notif_stop), ACTION_STOP, 3))
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun action(icon: Int, title: String, act: String, req: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, req, Intent(this, FloatingVolumeService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    // ---------- Botón flotante ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addBubble() {
        val view = buildBubble()
        val dm = resources.displayMetrics
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val savedX = Prefs.bubbleX(this@FloatingVolumeService)
            val savedY = Prefs.bubbleY(this@FloatingVolumeService)
            x = if (savedX >= 0) savedX else dm.widthPixels      // por defecto: borde derecho
            y = if (savedY >= 0) savedY else dm.heightPixels / 3
        }
        wm.addView(view, params)
        bubble = view
        view.post { snapToEdge(animate = false) }
        scheduleFade()
    }

    private fun buildBubble(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bubble_bg)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val up = iconButton(R.drawable.ic_plus, getString(R.string.tile_up))
        val down = iconButton(R.drawable.ic_minus, getString(R.string.tile_down))
        val grip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(4)).apply {
                setMargins(0, dp(6), 0, dp(6))
            }
            background = GradientDrawable().apply {
                setColor(0x66FFFFFF)
                cornerRadius = dp(2).toFloat()
            }
        }
        root.addView(up)
        root.addView(grip)
        root.addView(down)

        attachTouch(up, raise = true)
        attachTouch(down, raise = false)
        attachTouch(root, raise = null) // arrastrar desde la zona central
        return root
    }

    private fun iconButton(icon: Int, label: String) = ImageView(this).apply {
        setImageResource(icon)
        contentDescription = label
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        setPadding(dp(11), dp(11), dp(11), dp(11))
        isClickable = true
    }

    /**
     * Un solo manejador para toque, mantener presionado y arrastrar.
     * raise = true/false para los botones; null para la zona que solo arrastra.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(v: View, raise: Boolean?) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var repeated = false

        val repeater = object : Runnable {
            override fun run() {
                if (raise == null) return
                VolumeController.adjust(this@FloatingVolumeService, raise, Prefs.bubbleStream(this@FloatingVolumeService))
                repeated = true
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; repeated = false
                    wake()
                    if (raise != null) {
                        view.alpha = 0.55f
                        handler.postDelayed(repeater, REPEAT_DELAY_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && !repeated && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(repeater)
                        view.alpha = 1f
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        bubble?.let { wm.updateViewLayout(it, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeater)
                    view.alpha = 1f
                    if (dragging) {
                        snapToEdge(animate = true)
                    } else if (e.actionMasked == MotionEvent.ACTION_UP && raise != null && !repeated) {
                        VolumeController.adjust(this, raise, Prefs.bubbleStream(this))
                        view.performClick()
                    }
                    scheduleFade()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(animate: Boolean) {
        val b = bubble ?: return
        val dm = resources.displayMetrics
        val targetX = if (params.x + b.width / 2 < dm.widthPixels / 2) 0 else dm.widthPixels - b.width
        params.y = params.y.coerceIn(0, max(0, dm.heightPixels - b.height))

        if (!animate) {
            params.x = targetX
            if (b.isAttachedToWindow) wm.updateViewLayout(b, params)
            Prefs.setBubblePos(this, params.x, params.y)
            return
        }
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (b.isAttachedToWindow) wm.updateViewLayout(b, params)
            }
            start()
        }
        Prefs.setBubblePos(this, targetX, params.y)
    }

    private fun wake() {
        handler.removeCallbacks(fade)
        bubble?.animate()?.alpha(1f)?.setDuration(100)?.start()
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, FADE_AFTER_MS)
    }
}
