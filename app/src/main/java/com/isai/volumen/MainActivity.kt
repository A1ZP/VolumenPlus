package com.isai.volumen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var levelText: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnMute: Button
    private lateinit var switchBubble: Switch

    private var stream = AudioManager.STREAM_MUSIC
    private var syncing = false          // evita bucles al actualizar el switch por código
    private var waitingOverlay = false   // el usuario fue a dar el permiso de superposición
    private val handler = Handler(Looper.getMainLooper())

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshLevel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        levelText = findViewById(R.id.levelText)
        seek = findViewById(R.id.seek)
        btnMute = findViewById(R.id.btnMute)
        switchBubble = findViewById(R.id.switchBubble)

        setupDirectControls()
        setupBubble()
        setupTiles()
    }

    override fun onResume() {
        super.onResume()
        if (waitingOverlay && Settings.canDrawOverlays(this)) {
            waitingOverlay = false
            enableBubble()
        }
        syncSwitch()
        refreshLevel()
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(volumeReceiver)
    }

    // ---------- 1. Control directo ----------

    private fun setupDirectControls() {
        findViewById<RadioGroup>(R.id.streamGroup).setOnCheckedChangeListener { _, id ->
            stream = when (id) {
                R.id.rbRing -> AudioManager.STREAM_RING
                R.id.rbAlarm -> AudioManager.STREAM_ALARM
                else -> AudioManager.STREAM_MUSIC
            }
            refreshLevel()
        }

        repeatOnHold(findViewById(R.id.btnUp)) { VolumeController.adjust(this, true, stream) }
        repeatOnHold(findViewById(R.id.btnDown)) { VolumeController.adjust(this, false, stream) }

        btnMute.setOnClickListener {
            VolumeController.toggleMute(this, stream)
            refreshLevel()
        }
        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            VolumeController.showPanel(this, stream)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    VolumeController.set(this@MainActivity, stream, progress + VolumeController.min(this@MainActivity, stream))
                    updateLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) = refreshLevel()
        })
    }

    /** Toque = un paso; mantener = repetir. */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeatOnHold(view: View, step: () -> Unit) {
        val repeater = object : Runnable {
            override fun run() {
                step(); refreshLevel()
                handler.postDelayed(this, 110)
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    step(); refreshLevel()
                    handler.postDelayed(repeater, 450)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeater)
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun refreshLevel() {
        val am = VolumeController.audio(this)
        val min = VolumeController.min(this, stream)
        seek.max = am.getStreamMaxVolume(stream) - min
        seek.progress = am.getStreamVolume(stream) - min
        btnMute.setText(if (am.isStreamMute(stream)) R.string.btn_unmute else R.string.btn_mute)
        updateLabel()
    }

    private fun updateLabel() {
        val name = getString(
            when (stream) {
                AudioManager.STREAM_RING -> R.string.stream_ring
                AudioManager.STREAM_ALARM -> R.string.stream_alarm
                else -> R.string.stream_media
            }
        )
        levelText.text = getString(R.string.level_fmt, name, VolumeController.percent(this, stream))
    }

    // ---------- 2. Botón flotante ----------

    private fun setupBubble() {
        switchBubble.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            if (checked) {
                enableBubble()
            } else {
                Prefs.setBubbleEnabled(this, false)
                FloatingVolumeService.stop(this)
            }
        }

        val group = findViewById<RadioGroup>(R.id.bubbleStreamGroup)
        group.check(
            when (Prefs.bubbleStream(this)) {
                AudioManager.STREAM_MUSIC -> R.id.bsMedia
                AudioManager.STREAM_RING -> R.id.bsRing
                AudioManager.STREAM_ALARM -> R.id.bsAlarm
                else -> R.id.bsAuto
            }
        )
        group.setOnCheckedChangeListener { _, id ->
            Prefs.setBubbleStream(
                this,
                when (id) {
                    R.id.bsMedia -> AudioManager.STREAM_MUSIC
                    R.id.bsRing -> AudioManager.STREAM_RING
                    R.id.bsAlarm -> AudioManager.STREAM_ALARM
                    else -> VolumeController.STREAM_AUTO
                }
            )
        }
    }

    private fun enableBubble() {
        if (!Settings.canDrawOverlays(this)) {
            waitingOverlay = true
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            syncSwitch()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Opcional: solo para ver los controles en la notificación. El botón funciona igual sin esto.
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Prefs.setBubbleEnabled(this, true)
        FloatingVolumeService.start(this)
        syncSwitch()
    }

    private fun syncSwitch() {
        syncing = true
        switchBubble.isChecked = Prefs.bubbleEnabled(this) && Settings.canDrawOverlays(this)
        syncing = false
    }

    // ---------- 3. Mosaicos de Ajustes rápidos ----------

    private fun setupTiles() {
        val btn = findViewById<Button>(R.id.btnTiles)
        if (Build.VERSION.SDK_INT < 33) {
            btn.visibility = View.GONE   // en Android 12 o menos se agregan a mano (ver texto)
            return
        }
        btn.setOnClickListener {
            requestTile(VolumeUpTile::class.java, R.string.tile_up, R.drawable.ic_vol_up) {
                requestTile(VolumeDownTile::class.java, R.string.tile_down, R.drawable.ic_vol_down) {}
            }
        }
    }

    private fun requestTile(cls: Class<*>, label: Int, icon: Int, then: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return
        val sbm = getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(this, cls),
            getString(label),
            Icon.createWithResource(this, icon),
            mainExecutor
        ) { _ -> then() }
    }
}
