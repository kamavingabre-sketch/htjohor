package id.go.medanjohor.walkietalkie.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.go.medanjohor.walkietalkie.R
import id.go.medanjohor.walkietalkie.audio.AudioPlayer
import id.go.medanjohor.walkietalkie.audio.AudioRecorder
import id.go.medanjohor.walkietalkie.ui.MainActivity

class WalkieService : Service(), WalkieSocketListener {

    companion object {
        const val CHANNEL_ID = "walkie_johor_channel"
        const val NOTIF_ID = 1001
        const val EXTRA_USERNAME  = "username"
        const val EXTRA_USERID    = "userId"
        const val EXTRA_CHANNEL   = "channelId"
        const val EXTRA_KELURAHAN = "kelurahan"
    }

    inner class LocalBinder : Binder() {
        fun getService(): WalkieService = this@WalkieService
    }

    private val binder = LocalBinder()
    private lateinit var socket: WalkieWebSocket
    private lateinit var audioPlayer: AudioPlayer
    private var audioRecorder: AudioRecorder? = null
    private var isTransmitting = false

    var serviceListener: WalkieSocketListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        socket = WalkieWebSocket(this)
        audioPlayer = AudioPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Menghubungkan..."))
        socket.connect()
        val username  = intent?.getStringExtra(EXTRA_USERNAME)  ?: "Petugas"
        val userId    = intent?.getStringExtra(EXTRA_USERID)    ?: "unknown"
        val channelId = intent?.getStringExtra(EXTRA_CHANNEL)   ?: "umum"
        val kelurahan = intent?.getStringExtra(EXTRA_KELURAHAN) ?: ""

        socket.connect()
        // Join after a brief delay to allow connection
        android.os.Handler(mainLooper).postDelayed({
            socket.join(channelId, userId, username, kelurahan)
        }, 1500)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder?.stop()
        audioPlayer.release()
        socket.disconnect()
    }

    fun startTransmitting() {
        if (isTransmitting) return
        isTransmitting = true
        socket.sendPttStart()
        audioRecorder = AudioRecorder { data ->
            socket.sendAudio(data)
        }
        audioRecorder?.start()
    }

    fun stopTransmitting() {
        if (!isTransmitting) return
        isTransmitting = false
        audioRecorder?.stop()
        audioRecorder = null
        socket.sendPttEnd()
    }

    fun sendTextMessage(text: String) = socket.sendTextMessage(text)

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Walkie Johor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_walkie_notif)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Walkie Talkie Johor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Status koneksi Walkie Johor" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Delegate to UI listener
    override fun onConnected() { updateNotification("Terhubung"); serviceListener?.onConnected() }
    override fun onDisconnected() { updateNotification("Terputus — menghubungkan ulang..."); serviceListener?.onDisconnected() }
    override fun onError(message: String) { serviceListener?.onError(message) }
    override fun onJoined(channelId: String) { updateNotification("Saluran: $channelId"); serviceListener?.onJoined(channelId) }
    override fun onUserJoined(userId: String, username: String) { serviceListener?.onUserJoined(userId, username) }
    override fun onUserLeft(userId: String, username: String) { serviceListener?.onUserLeft(userId, username) }
    override fun onChannelUsers(users: List<ChannelUser>) { serviceListener?.onChannelUsers(users) }
    override fun onPttStart(userId: String, username: String) { serviceListener?.onPttStart(userId, username) }
    override fun onPttEnd(userId: String, username: String) { serviceListener?.onPttEnd(userId, username) }
    override fun onAudioReceived(data: ByteArray, userId: String, username: String) {
        audioPlayer.play(data)
        serviceListener?.onAudioReceived(data, userId, username)
    }
    override fun onTextMessage(userId: String, username: String, text: String, timestamp: Long) {
        serviceListener?.onTextMessage(userId, username, text, timestamp)
    }
}
