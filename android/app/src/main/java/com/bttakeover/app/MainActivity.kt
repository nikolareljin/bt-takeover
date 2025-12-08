package com.bttakeover.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothDevice.ACTION_FOUND
import android.content.Context
import android.content.BroadcastReceiver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.widget.Toast
import android.media.ToneGenerator
import android.companion.CompanionDeviceManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private var track: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var playing = false
    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    @Volatile private var isScanning = false
    @Volatile private var beeping = false
    private var toneGen: ToneGenerator? = null
    private val beepHandler = Handler(Looper.getMainLooper())
    private val beepIntervalMs = 900L
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                runOnUiThread {
                    stopNoise()
                    stopMedia()
                    txtStatus.text = getString(R.string.status_idle)
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = false
                    abandonAudioFocus()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // No-op: user can re-press Play; we avoid auto-resume
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Optional: could reduce volume here; keeping simple
            }
        }
    }

    private val prefs by lazy { getSharedPreferences("bttakeover_prefs", Context.MODE_PRIVATE) }

    private lateinit var txtStatus: TextView
    private lateinit var txtRoute: TextView
    private lateinit var chkLoop: CheckBox
    private lateinit var chkAutoSilence: CheckBox
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnBrowse: Button
    private lateinit var btnScan: Button
    private lateinit var rdNoise: RadioButton
    private lateinit var rdFile: RadioButton
    private lateinit var btnPick: Button
    private lateinit var btnBtSettings: Button
    private lateinit var btnTakeOver: Button
    private lateinit var btnStartBgScan: Button
    private lateinit var btnStopBgScan: Button
    private lateinit var txtDeviceId: EditText
    private lateinit var txtAudioFile: TextView

    companion object {
        private const val REQ_OPEN_AUDIO = 1001
        private const val REQ_BT_PERMS = 1002
        private const val REQ_CDM_ASSOC = 2002
        const val ACTION_TAKEOVER = "com.bttakeover.app.action.TAKEOVER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        chkLoop = findViewById(R.id.chkLoop)
        chkAutoSilence = findViewById(R.id.chkAutoSilence)
        txtStatus = findViewById(R.id.txtStatus)
        txtRoute = findViewById(R.id.txtRoute)
        btnBrowse = findViewById(R.id.btnBrowse)
        btnScan = findViewById(R.id.btnScan)
        btnPick = findViewById(R.id.btnPick)
        btnBtSettings = findViewById(R.id.btnBtSettings)
        btnTakeOver = findViewById(R.id.btnTakeOver)
        btnStartBgScan = findViewById(R.id.btnStartBgScan)
        btnStopBgScan = findViewById(R.id.btnStopBgScan)
        txtDeviceId = findViewById(R.id.txtDeviceId)
        txtAudioFile = findViewById(R.id.txtAudioFile)
        rdNoise = findViewById(R.id.rdNoise)
        rdFile = findViewById(R.id.rdFile)

        fun updateRouteLabel() {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bt = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            txtRoute.text = if (bt != null) {
                "Routing: Bluetooth A2DP (${bt.productName})"
            } else {
                "Routing: System default output"
            }
        }

        // Load prefs
        txtDeviceId.setText(prefs.getString("bluetoothId", ""))
        val savedUri = prefs.getString("audioUri", null)
        if (!savedUri.isNullOrEmpty()) {
            txtAudioFile.text = displayNameFromUri(Uri.parse(savedUri)) ?: getString(R.string.no_audio_chosen)
        }
        chkLoop.isChecked = prefs.getBoolean("loop", true)
        chkAutoSilence.isChecked = prefs.getBoolean("autoSilence", false)
        val mode = prefs.getString("mode", "noise")
        if (mode == "file") rdFile.isChecked = true else rdNoise.isChecked = true
        updateSourceUi()

        updateRouteLabel()

        txtDeviceId.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.edit().putString("bluetoothId", s?.toString() ?: "").apply()
            }
        })

        rdNoise.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                prefs.edit().putString("mode", "noise").apply()
                updateSourceUi()
            }
        }
        rdFile.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                prefs.edit().putString("mode", "file").apply()
                updateSourceUi()
            }
        }

        chkAutoSilence.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("autoSilence", isChecked).apply()
        }

        btnPlay.setOnClickListener {
            if (playing) return@setOnClickListener
            // Set media stream volume to max for takeover effect
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            val fileMode = rdFile.isChecked
            val audio = prefs.getString("audioUri", null)
            if (!fileMode) {
                startNoise(chkLoop.isChecked) {
                    runOnUiThread {
                        txtStatus.text = getString(R.string.status_playing)
                        btnPlay.isEnabled = false
                        btnStop.isEnabled = true
                    }
                }
            } else {
                if (audio.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.select_audio_prompt), Toast.LENGTH_SHORT).show()
                    applyAudioFileStyling(false)
                    return@setOnClickListener
                }
                startMedia(Uri.parse(audio), chkLoop.isChecked) {
                    runOnUiThread {
                        txtStatus.text = getString(R.string.status_playing)
                        btnPlay.isEnabled = false
                        btnStop.isEnabled = true
                    }
                }
            }
        }

        btnStop.setOnClickListener {
            stopNoise()
            stopMedia()
            abandonAudioFocus()
            txtStatus.text = getString(R.string.status_idle)
            btnPlay.isEnabled = true
            btnStop.isEnabled = false
        }

        // Initial state
        btnStop.isEnabled = false
        txtStatus.text = getString(R.string.status_idle)

        btnBrowse.setOnClickListener {
            openAudioPicker()
        }

        btnScan.setOnClickListener {
            ensureBtPermissionThen(requireScan = true) {
                scanForConfiguredDevice()
            }
        }

        btnPick.setOnClickListener {
            ensureBtPermissionThen { pickFromPaired() }
        }

        btnBtSettings.setOnClickListener {
            openBluetoothSettings()
        }

        btnTakeOver.setOnClickListener {
            ensureBtPermissionThen(requireScan = true) {
                takeOver()
            }
        }

        btnStartBgScan.setOnClickListener {
            ensureBtPermissionThen(requireScan = true) {
                ensureNotificationPermissionThen {
                    startForegroundScanService()
                }
            }
        }
        btnStopBgScan.setOnClickListener {
            stopService(Intent(this, ScanService::class.java).setAction(ScanService.ACTION_STOP))
        }

        // Prepare A2DP + HEADSET profile proxies to check/drive connection
        ensureBtPermissionThen {
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(this, object: BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                    if (profile == BluetoothProfile.HEADSET) headsetProfile = null
                }
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy
                        checkBluetoothConnection()
                    }
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProfile = proxy
                    }
                }
            }, BluetoothProfile.A2DP)
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(this, object: BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) headsetProfile = null
                }
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HEADSET) {
                        headsetProfile = proxy
                    }
                }
            }, BluetoothProfile.HEADSET)
        }

        // If launched from notification action to take over
        intent?.action?.let { act ->
            if (act == ACTION_TAKEOVER) {
                ensureBtPermissionThen(requireScan = true) { takeOver() }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.action?.let { act ->
            if (act == ACTION_TAKEOVER) {
                ensureBtPermissionThen(requireScan = true) { takeOver() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNoise()
        stopMedia()
        abandonAudioFocus()
        stopBeeping()
        a2dpProfile?.let { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        headsetProfile?.let { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        stopDiscovery()
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_OPEN_AUDIO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OPEN_AUDIO && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                // Persist permission to use across restarts
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            prefs.edit().putString("audioUri", uri.toString()).apply()
            txtAudioFile.text = displayNameFromUri(uri) ?: uri.toString()
            // Switch to file mode automatically when selecting a file
            rdFile.isChecked = true
            applyAudioFileStyling(true)
        } else if (requestCode == REQ_CDM_ASSOC && resultCode == Activity.RESULT_OK) {
            try {
                val device = data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                if (device != null) {
                    // Save device address for future operations
                    txtDeviceId.setText(device.address ?: device.name ?: "")
                    prefs.edit().putString("bluetoothId", device.address ?: device.name ?: "").apply()
                    // Kick off takeover with the selected device
                    ensureBtPermissionThen(requireScan = false) {
                        takeOverWithDevice(device)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun displayNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun startNoise(loop: Boolean, onStart: () -> Unit) {
        requestAudioFocus()
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setChannelMask(channelConfig)
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .build()

        val audioTrack = AudioTrack(
            attrs,
            format,
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track = audioTrack
        playing = true

        Thread {
            try {
                audioTrack.play()
                onStart()
                val frameCount = 1024
                val buf = ShortArray(frameCount * 2) // stereo
                val rnd = java.util.Random()
                while (playing) {
                    // Generate white noise (16-bit signed)
                    for (i in buf.indices) {
                        // simple white noise from Gaussian; scaled
                        val v = (rnd.nextGaussian() * Short.MAX_VALUE / 3.5).toInt()
                        buf[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    val bytes = ShortArrayToByteArray(buf)
                    var written = 0
                    while (written < bytes.size && playing) {
                        val n = audioTrack.write(bytes, written, bytes.size - written)
                        if (n <= 0) break
                        written += n
                    }
                    if (!loop) break
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack.stop()
                } catch (_: Exception) {}
                try {
                    audioTrack.release()
                } catch (_: Exception) {}
                playing = false
            }
        }.start()
    }

    private fun stopNoise() {
        playing = false
        track?.let { t ->
            try { t.pause() } catch (_: Exception) {}
            try { t.flush() } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
        }
        track = null
    }

    private fun startMedia(uri: Uri, loop: Boolean, onStart: () -> Unit) {
        try {
            requestAudioFocus()
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = loop
            mp.setOnPreparedListener {
                onStart()
                it.start()
            }
            mp.setOnCompletionListener {
                playing = false
                runOnUiThread {
                    txtStatus.text = getString(R.string.status_idle)
                    btnPlay.isEnabled = true
                    btnStop.isEnabled = false
                }
            }
            mp.prepareAsync()
            playing = true
        } catch (_: Exception) {
            runOnUiThread {
                txtStatus.text = "Failed to play selected audio"
            }
            stopMedia()
        }
    }

    private fun stopMedia() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(focusListener)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { false }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {}
        focusRequest = null
    }

    private fun updateSourceUi() {
        val fileMode = rdFile.isChecked
        btnBrowse.isEnabled = fileMode
        txtAudioFile.visibility = if (fileMode) View.VISIBLE else View.GONE
        if (fileMode) {
            val hasFile = !prefs.getString("audioUri", null).isNullOrEmpty()
            applyAudioFileStyling(hasFile)
        } else {
            clearAudioFileStyling()
        }
    }

    private fun applyAudioFileStyling(hasFile: Boolean) {
        if (!hasFile) {
            txtAudioFile.setTextColor(ContextCompat.getColor(this, R.color.error))
            txtAudioFile.setTypeface(null, Typeface.ITALIC)
            txtAudioFile.setBackgroundResource(R.drawable.bg_warning)
        } else {
            clearAudioFileStyling()
        }
    }

    private fun clearAudioFileStyling() {
        txtAudioFile.setTextColor(ContextCompat.getColor(this, R.color.fg))
        txtAudioFile.setTypeface(null, Typeface.NORMAL)
        txtAudioFile.background = null
    }

    private fun pickFromPaired() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            val devices = adapter.bondedDevices?.toList() ?: emptyList()
            if (devices.isEmpty()) {
                runOnUiThread { txtRoute.text = getString(R.string.not_connected) }
                return
            }
            val labels = devices.map { d ->
                val name = d.name ?: "Unknown"
                "$name (${d.address})"
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.pick_from_paired))
                .setItems(labels) { _, which ->
                    val d = devices[which]
                    val value = d.address ?: d.name ?: ""
                    txtDeviceId.setText(value)
                    prefs.edit().putString("bluetoothId", value).apply()
                    checkBluetoothConnection()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (_: Exception) {}
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {}
    }

    private fun ensureBtPermissionThen(requireScan: Boolean = false, action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val toRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toRequest += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (requireScan && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toRequest += Manifest.permission.BLUETOOTH_SCAN
            }
            if (toRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_BT_PERMS)
            } else action()
        } else {
            if (requireScan && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_BT_PERMS)
            } else action()
        }
    }

    private fun ensureNotificationPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_BT_PERMS)
            } else action()
        } else action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMS) {
            // Try again regardless; UI can handle failures
            checkBluetoothConnection()
        }
    }

    private fun checkBluetoothConnection() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                // Prompt user to enable Bluetooth / open settings
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                txtRoute.text = getString(R.string.not_connected)
                return
            }
            val target = prefs.getString("bluetoothId", "")?.trim()?.lowercase() ?: ""
            val a2dp = a2dpProfile
            val connected: List<BluetoothDevice> = if (a2dp != null) a2dp.connectedDevices else emptyList()
            val match = connected.firstOrNull { d ->
                val addr = d.address?.lowercase()
                val name = d.name?.lowercase()
                target.isNotEmpty() && (addr == target || addr?.replace(":", "") == target.replace(":", "") || (name?.contains(target) ?: false))
            }
            if (match != null) {
                txtRoute.text = getString(R.string.connected_to, match.name ?: match.address)
            } else {
                txtRoute.text = getString(R.string.not_connected)
            }
        } catch (_: Exception) {
            txtRoute.text = getString(R.string.not_connected)
        }
    }

    private fun scanForConfiguredDevice() {
        if (isScanning) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        val input = prefs.getString("bluetoothId", "")?.trim() ?: ""
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter device ID or MAC", Toast.LENGTH_SHORT).show()
            return
        }
        val tLower = input.lowercase()
        isScanning = true
        runOnUiThread { txtStatus.text = getString(R.string.searching_device) }

        val rx = object: BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_FOUND -> {
                        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null) {
                            val addr = (dev.address ?: "").lowercase()
                            val addrFlat = addr.replace(":", "")
                            val name = (dev.name ?: "").lowercase()
                            val macNormalized = normalizedMac(input)
                            val matched = when {
                                macNormalized != null -> addr == macNormalized
                                else -> addr == tLower || addrFlat == tLower || name.contains(tLower)
                            }
                            if (matched) {
                                onDeviceFound(dev)
                            }
                        }
                    }
                    ACTION_DISCOVERY_FINISHED -> {
                        // Restart scanning automatically for a couple cycles when user wants presence detection
                        if (isScanning) {
                            try { adapter.startDiscovery() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        discoveryReceiver = rx
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(ACTION_FOUND)
                addAction(ACTION_DISCOVERY_FINISHED)
            }
            registerReceiver(rx, filter)
            adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: Exception) {
            stopDiscovery()
            isScanning = false
        }
    }

    private fun stopDiscovery() {
        isScanning = false
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        discoveryReceiver?.let { r ->
            try { unregisterReceiver(r) } catch (_: Exception) {}
        }
        discoveryReceiver = null
    }

    private fun onDeviceFound(dev: BluetoothDevice) {
        stopDiscovery()
        runOnUiThread {
            // Visuals
            txtRoute.text = getString(R.string.found_device_short, dev.name ?: dev.address)
            txtRoute.setBackgroundResource(R.drawable.bg_success)
            Handler(Looper.getMainLooper()).postDelayed({
                txtRoute.background = null
            }, 3000)
            // Beep continuously until silenced
            startBeeping()
            // Bring app to front
            bringAppToFront()
            // Alert
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.device_found_title))
                .setMessage(getString(R.string.device_found_message, dev.name ?: dev.address))
                .setPositiveButton(getString(R.string.take_over_now)) { _, _ ->
                    btnTakeOver.performClick()
                }
                .setNegativeButton(getString(R.string.silence)) { _, _ -> stopBeeping() }
                .show()
        }
    }

    private fun startBeeping() {
        if (beeping) return
        beeping = true
        try { if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) } catch (_: Exception) {}
        val task = object: Runnable {
            override fun run() {
                if (!beeping) return
                try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } catch (_: Exception) {}
                beepHandler.postDelayed(this, beepIntervalMs)
            }
        }
        beepHandler.post(task)
    }

    private fun stopBeeping() {
        beeping = false
        beepHandler.removeCallbacksAndMessages(null)
        try { toneGen?.release() } catch (_: Exception) {}
        toneGen = null
    }

    private fun bringAppToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun startForegroundScanService() {
        try {
            val id = prefs.getString("bluetoothId", "") ?: ""
            val intent = Intent(this, ScanService::class.java).apply {
                action = ScanService.ACTION_START
                putExtra(ScanService.EXTRA_TARGET, id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else startService(intent)
            Toast.makeText(this, "Background scan started", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    // Advanced takeover: find device by ID, ensure bonded, disconnect others and connect to A2DP/HEADSET via reflection
    private fun takeOver() {
        Thread {
            try {
                runOnUiThread { txtStatus.text = getString(R.string.searching_device) }
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@Thread
                if (!adapter.isEnabled) {
                    runOnUiThread { txtStatus.text = getString(R.string.not_connected) }
                    openBluetoothSettings()
                    return@Thread
                }

                val input = prefs.getString("bluetoothId", "")?.trim() ?: ""
                if (input.isEmpty()) {
                    runOnUiThread { Toast.makeText(this, "Enter device ID or MAC", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }

                val device = findOrDiscoverDevice(adapter, input, 25000)
                if (device == null) {
                    runOnUiThread { txtStatus.text = getString(R.string.device_not_found) }
                    return@Thread
                }

                if (device.bondState != BOND_BONDED) {
                    runOnUiThread { txtStatus.text = getString(R.string.pairing_attempt) }
                    val ok = bondDevice(device, 20000)
                    runOnUiThread { txtStatus.text = if (ok) getString(R.string.pairing_ok) else getString(R.string.pairing_failed) }
                    if (!ok) return@Thread
                }

                // Disconnect others on A2DP/HEADSET and connect target via reflection
                runOnUiThread { txtStatus.text = getString(R.string.disconnecting_others) }
                a2dpProfile?.let { disconnectOthers(it, device) }
                headsetProfile?.let { disconnectOthers(it, device) }

                var anyConnected = false
                runOnUiThread { txtStatus.text = getString(R.string.connecting_a2dp) }
                anyConnected = connectProfile(a2dpProfile, device) || anyConnected
                Thread.sleep(500)
                runOnUiThread { txtStatus.text = getString(R.string.connecting_headset) }
                anyConnected = connectProfile(headsetProfile, device) || anyConnected

                // Give the system a moment to finalize routing
                Thread.sleep(1200)
                runOnUiThread {
                    if (anyConnected) {
                        txtStatus.text = getString(R.string.takeover_done)
                        checkBluetoothConnection()
                        if (!playing) btnPlay.performClick()
                        if (prefs.getBoolean("autoSilence", false)) {
                            // Stop any ongoing beeping in activity and service
                            stopBeeping()
                            try { startService(Intent(this@MainActivity, ScanService::class.java).setAction(ScanService.ACTION_SILENCE)) } catch (_: Exception) {}
                        }
                    } else {
                        txtStatus.text = getString(R.string.takeover_failed)
                        // Offer system device picker as a fallback
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.system_picker_title))
                            .setMessage(getString(R.string.system_picker_message))
                            .setPositiveButton(getString(R.string.open_picker)) { _, _ -> associateViaCdm() }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { txtStatus.text = getString(R.string.takeover_failed) }
            }
        }.start()
    }

    private fun takeOverWithDevice(device: BluetoothDevice) {
        Thread {
            try {
                if (device.bondState != BOND_BONDED) {
                    runOnUiThread { txtStatus.text = getString(R.string.pairing_attempt) }
                    val ok = bondDevice(device, 20000)
                    runOnUiThread { txtStatus.text = if (ok) getString(R.string.pairing_ok) else getString(R.string.pairing_failed) }
                    if (!ok) return@Thread
                }
                runOnUiThread { txtStatus.text = getString(R.string.disconnecting_others) }
                a2dpProfile?.let { disconnectOthers(it, device) }
                headsetProfile?.let { disconnectOthers(it, device) }
                var anyConnected = false
                runOnUiThread { txtStatus.text = getString(R.string.connecting_a2dp) }
                anyConnected = connectProfile(a2dpProfile, device) || anyConnected
                Thread.sleep(500)
                runOnUiThread { txtStatus.text = getString(R.string.connecting_headset) }
                anyConnected = connectProfile(headsetProfile, device) || anyConnected
                Thread.sleep(1200)
                runOnUiThread {
                    if (anyConnected) {
                        txtStatus.text = getString(R.string.takeover_done)
                        checkBluetoothConnection()
                        if (!playing) btnPlay.performClick()
                        if (prefs.getBoolean("autoSilence", false)) {
                            stopBeeping()
                            try { startService(Intent(this@MainActivity, ScanService::class.java).setAction(ScanService.ACTION_SILENCE)) } catch (_: Exception) {}
                        }
                    } else {
                        txtStatus.text = getString(R.string.takeover_failed)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { txtStatus.text = getString(R.string.takeover_failed) }
            }
        }.start()
    }

    private fun associateViaCdm() {
        try {
            val mgr = getSystemService(CompanionDeviceManager::class.java)
            val input = prefs.getString("bluetoothId", "")?.trim() ?: ""
            val namePattern = if (input.isNotEmpty()) Pattern.compile(Pattern.quote(input), Pattern.CASE_INSENSITIVE) else Pattern.compile(".*")
            val filter = BluetoothDeviceFilter.Builder()
                .setNamePattern(namePattern)
                .build()
            val request = AssociationRequest.Builder()
                .addDeviceFilter(filter)
                .setSingleDevice(true)
                .build()
            mgr.associate(request, object: CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: android.content.IntentSender) {
                    try {
                        startIntentSenderForResult(chooserLauncher, REQ_CDM_ASSOC, null, 0, 0, 0)
                    } catch (_: Exception) {}
                }
                override fun onFailure(error: CharSequence?) {
                    Toast.makeText(this@MainActivity, error ?: "Association failed", Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            Toast.makeText(this, "Association not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isMacLike(v: String): Boolean {
        val s = v.trim()
        val re = Regex("""^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$""")
        val re2 = Regex("""^[0-9A-Fa-f]{12}$""")
        return re.matches(s) || re2.matches(s)
    }

    private fun normalizedMac(v: String): String? {
        if (!isMacLike(v)) return null
        val hex = v.replace(":", "").replace("-", "").lowercase()
        return hex.chunked(2).joinToString(":") { it }
    }

    private fun findOrDiscoverDevice(adapter: BluetoothAdapter, idOrName: String, timeoutMs: Long): BluetoothDevice? {
        val target = idOrName.trim()
        // 1) If MAC provided, get remote device directly
        normalizedMac(target)?.let { mac ->
            return try { adapter.getRemoteDevice(mac) } catch (_: Exception) { null }
        }
        // 2) Search bonded devices by name contains or exact address match without separators
        val bonded = adapter.bondedDevices?.toList().orEmpty()
        val tLower = target.lowercase()
        val matchBonded = bonded.firstOrNull { d ->
            val addr = d.address?.lowercase() ?: ""
            val addrFlat = addr.replace(":", "")
            val name = d.name?.lowercase() ?: ""
            addr == tLower || addrFlat == tLower || name.contains(tLower)
        }
        if (matchBonded != null) return matchBonded

        // 3) As a fallback, try classic discovery and match by name
        val found = arrayOfNulls<BluetoothDevice>(1)
        val latch = java.util.concurrent.CountDownLatch(1)
        val rx = object: android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_FOUND -> {
                        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null) {
                            val name = (dev.name ?: "").lowercase()
                            if (name.contains(tLower)) {
                                found[0] = dev
                                try { unregisterReceiver(this) } catch (_: Exception) {}
                                adapter.cancelDiscovery()
                                latch.countDown()
                            }
                        }
                    }
                    ACTION_DISCOVERY_FINISHED -> {
                        try { unregisterReceiver(this) } catch (_: Exception) {}
                        latch.countDown()
                    }
                }
            }
        }
        try {
            val flt = android.content.IntentFilter().apply {
                addAction(ACTION_FOUND)
                addAction(ACTION_DISCOVERY_FINISHED)
            }
            registerReceiver(rx, flt)
            adapter.startDiscovery()
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        } finally {
            try { unregisterReceiver(rx) } catch (_: Exception) {}
            try { adapter.cancelDiscovery() } catch (_: Exception) {}
        }
        return found[0]
    }

    private fun bondDevice(device: BluetoothDevice, timeoutMs: Long): Boolean {
        if (device.bondState == BOND_BONDED) return true
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        val rx = object: android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_BOND_STATE_CHANGED) {
                    val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (d?.address == device.address) {
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BOND_NONE)) {
                            BOND_BONDED -> { ok = true; try { unregisterReceiver(this) } catch (_: Exception) {}; latch.countDown() }
                            BOND_NONE -> { ok = false; try { unregisterReceiver(this) } catch (_: Exception) {}; latch.countDown() }
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(rx, android.content.IntentFilter(ACTION_BOND_STATE_CHANGED))
            val started = device.createBond()
            if (!started) { try { unregisterReceiver(rx) } catch (_: Exception) {}; return false }
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        } finally {
            try { unregisterReceiver(rx) } catch (_: Exception) {}
        }
        return ok
    }

    private fun disconnectOthers(profile: BluetoothProfile, keep: BluetoothDevice) {
        try {
            val connected = profile.connectedDevices ?: emptyList()
            val m = profile.javaClass.methods.firstOrNull { it.name == "disconnect" && it.parameterTypes.size == 1 }
            if (m != null) {
                for (d in connected) {
                    if (d.address != keep.address) {
                        try { m.invoke(profile, d) } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun connectProfile(profile: BluetoothProfile?, device: BluetoothDevice): Boolean {
        if (profile == null) return false
        return try {
            val m = profile.javaClass.methods.firstOrNull { it.name == "connect" && it.parameterTypes.size == 1 }
            if (m != null) {
                val r = m.invoke(profile, device)
                (r as? Boolean) ?: true
            } else false
        } catch (_: Exception) { false }
    }

    private fun ShortArrayToByteArray(src: ShortArray): ByteArray {
        val out = ByteArray(src.size * 2)
        var j = 0
        for (s in src) {
            out[j++] = (s.toInt() and 0xFF).toByte()
            out[j++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }
}
