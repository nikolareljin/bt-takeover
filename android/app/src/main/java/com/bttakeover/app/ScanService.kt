package com.bttakeover.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_FOUND
import android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var toneGen: ToneGenerator? = null
    @Volatile private var beeping = false
    @Volatile private var scanning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startScanning(intent.getStringExtra(EXTRA_TARGET))
            ACTION_SILENCE -> { stopBeeping(); stopSelf() }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBeeping()
        stopDiscovery()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startScanning(target: String?) {
        val id = target?.takeIf { it.isNotBlank() }
            ?: getSharedPreferences("bttakeover_prefs", Context.MODE_PRIVATE).getString("bluetoothId", "")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val contentIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP) },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_scanning_title))
            .setContentText(getString(R.string.notif_scanning_text, id ?: ""))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .build()
        startForeground(NOTIF_ID, notif)

        if (id.isNullOrBlank()) return

        val lower = id.lowercase()
        val macNorm = normalizeMac(id)
        scanning = true
        val rx = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_FOUND -> {
                        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null) {
                            val addr = (dev.address ?: "").lowercase()
                            val name = (dev.name ?: "").lowercase()
                            val flat = addr.replace(":", "")
                            val match = when {
                                macNorm != null -> addr == macNorm
                                else -> addr == lower || flat == lower || name.contains(lower)
                            }
                            if (match) onFound(dev)
                        }
                    }
                    ACTION_DISCOVERY_FINISHED -> if (scanning) try { adapter.startDiscovery() } catch (_: Exception) {}
                }
            }
        }
        receiver = rx
        try {
            registerReceiver(rx, IntentFilter().apply { addAction(ACTION_FOUND); addAction(ACTION_DISCOVERY_FINISHED) })
            adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun stopDiscovery() {
        scanning = false
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun onFound(dev: BluetoothDevice) {
        stopDiscovery()
        startBeeping()
        val openIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val takeoverIntent = PendingIntent.getActivity(
            this, 4,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_TAKEOVER
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val silenceIntent = PendingIntent.getService(
            this, 3,
            Intent(this, ScanService::class.java).setAction(ACTION_SILENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_found_title))
            .setContentText(getString(R.string.notif_found_text, dev.name ?: dev.address))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.open_app), openIntent)
            .addAction(0, getString(R.string.take_over), takeoverIntent)
            .addAction(0, getString(R.string.silence), silenceIntent)
            .setAutoCancel(false)
        // Attempt full-screen intent to bring app forward
        builder.setFullScreenIntent(openIntent, true)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, builder.build())
    }

    private fun startBeeping() {
        if (beeping) return
        beeping = true
        try { if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) } catch (_: Exception) {}
        val task = object: Runnable {
            override fun run() {
                if (!beeping) return
                try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } catch (_: Exception) {}
                handler.postDelayed(this, 900)
            }
        }
        handler.post(task)
    }

    private fun stopBeeping() {
        beeping = false
        handler.removeCallbacksAndMessages(null)
        try { toneGen?.release() } catch (_: Exception) {}
        toneGen = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun normalizeMac(v: String): String? {
        val hex = v.replace(":", "").replace("-", "").lowercase()
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":")
    }

    companion object {
        const val CHANNEL_ID = "presence_scan"
        const val NOTIF_ID = 41
        const val ACTION_START = "com.bttakeover.app.action.START"
        const val ACTION_SILENCE = "com.bttakeover.app.action.SILENCE"
        const val ACTION_STOP = "com.bttakeover.app.action.STOP"
        const val EXTRA_TARGET = "target"
    }
}
