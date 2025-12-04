package com.bttakeover.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private var track: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var playing = false
    private var a2dpProfile: BluetoothProfile? = null

    private val prefs by lazy { getSharedPreferences("bttakeover_prefs", Context.MODE_PRIVATE) }

    private lateinit var txtStatus: TextView
    private lateinit var txtRoute: TextView
    private lateinit var chkLoop: CheckBox
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnBrowse: Button
    private lateinit var btnScan: Button
    private lateinit var rdNoise: RadioButton
    private lateinit var rdFile: RadioButton
    private lateinit var btnPick: Button
    private lateinit var btnBtSettings: Button
    private lateinit var txtDeviceId: EditText
    private lateinit var txtAudioFile: TextView

    companion object {
        private const val REQ_OPEN_AUDIO = 1001
        private const val REQ_BT_PERMS = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        chkLoop = findViewById(R.id.chkLoop)
        txtStatus = findViewById(R.id.txtStatus)
        txtRoute = findViewById(R.id.txtRoute)
        btnBrowse = findViewById(R.id.btnBrowse)
        btnScan = findViewById(R.id.btnScan)
        btnPick = findViewById(R.id.btnPick)
        btnBtSettings = findViewById(R.id.btnBtSettings)
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
            ensureBtPermissionThen {
                checkBluetoothConnection()
            }
        }

        btnPick.setOnClickListener {
            ensureBtPermissionThen { pickFromPaired() }
        }

        btnBtSettings.setOnClickListener {
            openBluetoothSettings()
        }

        // Prepare A2DP profile proxy to check connection
        ensureBtPermissionThen {
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(this, object: BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dpProfile = null
                }
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy
                        checkBluetoothConnection()
                    }
                }
            }, BluetoothProfile.A2DP)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNoise()
        stopMedia()
        a2dpProfile?.let { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, it) }
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

    private fun ensureBtPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perm = Manifest.permission.BLUETOOTH_CONNECT
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
