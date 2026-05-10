package id.go.medanjohor.walkietalkie.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import id.go.medanjohor.walkietalkie.R
import id.go.medanjohor.walkietalkie.network.ChannelUser
import id.go.medanjohor.walkietalkie.network.WalkieService
import id.go.medanjohor.walkietalkie.network.WalkieSocketListener
import id.go.medanjohor.walkietalkie.utils.CHANNEL_LIST
import id.go.medanjohor.walkietalkie.utils.UserPrefs

class MainActivity : AppCompatActivity(), WalkieSocketListener {

    private var walkieService: WalkieService? = null
    private var bound = false
    private lateinit var prefs: UserPrefs

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvChannel: TextView
    private lateinit var tvOnlineCount: TextView
    private lateinit var tvSpeaker: TextView
    private lateinit var btnPtt: MaterialButton
    private lateinit var tvUsername: TextView
    private lateinit var llSpeakingIndicator: LinearLayout

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as WalkieService.LocalBinder
            walkieService = b.getService()
            walkieService?.serviceListener = this@MainActivity
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            walkieService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = UserPrefs(this)

        tvStatus           = findViewById(R.id.tvStatus)
        tvChannel          = findViewById(R.id.tvChannel)
        tvOnlineCount      = findViewById(R.id.tvOnlineCount)
        tvSpeaker          = findViewById(R.id.tvSpeaker)
        btnPtt             = findViewById(R.id.btnPtt)
        tvUsername         = findViewById(R.id.tvUsername)
        llSpeakingIndicator = findViewById(R.id.llSpeakingIndicator)

        tvUsername.text = prefs.username
        tvChannel.text  = prefs.lastChannel
        llSpeakingIndicator.visibility = View.GONE

        setupPttButton()
        setupChannelButton()
        setupMenuButton()

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val denied = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (denied.isEmpty()) startWalkieService()
        else ActivityCompat.requestPermissions(this, denied.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startWalkieService()
            else Snackbar.make(btnPtt, "Izin mikrofon diperlukan", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun startWalkieService() {
        val intent = Intent(this, WalkieService::class.java).apply {
            putExtra(WalkieService.EXTRA_USERNAME,  prefs.username)
            putExtra(WalkieService.EXTRA_USERID,    prefs.userId)
            putExtra(WalkieService.EXTRA_CHANNEL,   prefs.lastChannel)
            putExtra(WalkieService.EXTRA_KELURAHAN, prefs.kelurahan)
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)
    }

    private fun setupPttButton() {
        btnPtt.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    walkieService?.startTransmitting()
                    btnPtt.text = "TRANSMIT"
                    btnPtt.setBackgroundColor(ContextCompat.getColor(this, R.color.ptt_active))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    walkieService?.stopTransmitting()
                    btnPtt.text = getString(R.string.ptt_label)
                    btnPtt.setBackgroundColor(ContextCompat.getColor(this, R.color.ptt_idle))
                }
            }
            v.performClick()
            true
        }
    }

    private fun setupChannelButton() {
        val tvChangeChannel = findViewById<TextView>(R.id.tvChangeChannel)
        tvChangeChannel.setOnClickListener {
            val adapter = ArrayAdapter(this, R.layout.item_dropdown, CHANNEL_LIST)
            val ddView = AutoCompleteTextView(this)
            ddView.setAdapter(adapter)
            ddView.threshold = 0
            MaterialAlertDialogBuilder(this)
                .setTitle("Pilih Saluran")
                .setView(ddView)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Pindah") { _, _ ->
                    val ch = ddView.text.toString().ifBlank { prefs.lastChannel }
                    prefs.lastChannel = ch
                    tvChannel.text = ch
                    // Restart service with new channel
                    if (bound) unbindService(serviceConn)
                    stopService(Intent(this, WalkieService::class.java))
                    startWalkieService()
                }
                .show()
            ddView.postDelayed({ ddView.showDropDown() }, 100)
        }
    }

    private fun setupMenuButton() {
        val btnMenu = findViewById<View>(R.id.btnMenu)
        btnMenu.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(prefs.username)
                .setMessage("Kelurahan: ${prefs.kelurahan}\nSaluran: ${prefs.lastChannel}")
                .setPositiveButton("Tutup", null)
                .setNeutralButton("Keluar") { _, _ ->
                    prefs.logout()
                    stopService(Intent(this, WalkieService::class.java))
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            walkieService?.serviceListener = null
            unbindService(serviceConn)
        }
    }

    // Socket callbacks — always run on main thread
    override fun onConnected() = runOnUiThread {
        tvStatus.text = "● Terhubung"
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_online))
    }

    override fun onDisconnected() = runOnUiThread {
        tvStatus.text = "● Terputus"
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
        tvOnlineCount.text = "0 pengguna online"
    }

    override fun onError(message: String) = runOnUiThread {
        Snackbar.make(btnPtt, "Error: $message", Snackbar.LENGTH_SHORT).show()
    }

    override fun onJoined(channelId: String) = runOnUiThread {
        tvChannel.text = channelId
    }

    override fun onUserJoined(userId: String, username: String) = runOnUiThread {
        Snackbar.make(btnPtt, "$username bergabung", Snackbar.LENGTH_SHORT).show()
    }

    override fun onUserLeft(userId: String, username: String) = runOnUiThread {
        Snackbar.make(btnPtt, "$username keluar", Snackbar.LENGTH_SHORT).show()
    }

    override fun onChannelUsers(users: List<ChannelUser>) = runOnUiThread {
        tvOnlineCount.text = "${users.size} pengguna online"
    }

    override fun onPttStart(userId: String, username: String) = runOnUiThread {
        tvSpeaker.text = username
        llSpeakingIndicator.visibility = View.VISIBLE
    }

    override fun onPttEnd(userId: String, username: String) = runOnUiThread {
        llSpeakingIndicator.visibility = View.GONE
    }

    override fun onAudioReceived(data: ByteArray, userId: String, username: String) { /* handled by service */ }
    override fun onTextMessage(userId: String, username: String, text: String, timestamp: Long) = runOnUiThread {
        Snackbar.make(btnPtt, "$username: $text", Snackbar.LENGTH_LONG).show()
    }
}
