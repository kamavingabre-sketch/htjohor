package id.go.medanjohor.walkietalkie.network

import android.util.Log
import id.go.medanjohor.walkietalkie.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WalkieWebSocket(private val listener: WalkieSocketListener) {

    private val TAG = "WalkieWebSocket"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .build()

    private var ws: WebSocket? = null
    private var isConnected = false
    private var shouldReconnect = true
    private var reconnectDelay = 2000L

    fun connect(serverUrl: String = BuildConfig.SERVER_URL) {
        shouldReconnect = true
        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectDelay = 2000L
                Log.d(TAG, "Connected to $serverUrl")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    handleMessage(json)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse error: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Split header (JSON line) and raw audio
                val raw = bytes.toByteArray()
                val newlineIdx = raw.indexOf('\n'.code.toByte())
                if (newlineIdx < 0) return
                val headerJson = JSONObject(String(raw.copyOf(newlineIdx)))
                val audioData = raw.copyOfRange(newlineIdx + 1, raw.size)
                val userId = headerJson.optString("userId", "")
                val username = headerJson.optString("username", "")
                listener.onAudioReceived(audioData, userId, username)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "Connection closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "Connection closed")
                listener.onDisconnected()
                scheduleReconnect(serverUrl)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "Connection failed: ${t.message}")
                listener.onError(t.message ?: "Connection failed")
                listener.onDisconnected()
                scheduleReconnect(serverUrl)
            }
        })
    }

    private fun handleMessage(json: JSONObject) {
        when (json.getString("type")) {
            "joined"       -> listener.onJoined(json.getString("channelId"))
            "user_joined"  -> listener.onUserJoined(json.getString("userId"), json.getString("username"))
            "user_left"    -> listener.onUserLeft(json.getString("userId"), json.getString("username"))
            "channel_users" -> {
                val usersArr = json.getJSONArray("users")
                val users = mutableListOf<ChannelUser>()
                for (i in 0 until usersArr.length()) {
                    val u = usersArr.getJSONObject(i)
                    users.add(ChannelUser(u.getString("userId"), u.getString("username"), u.optString("kelurahan", "")))
                }
                listener.onChannelUsers(users)
            }
            "ptt_start"    -> listener.onPttStart(json.getString("userId"), json.getString("username"))
            "ptt_end"      -> listener.onPttEnd(json.getString("userId"), json.getString("username"))
            "text_message" -> listener.onTextMessage(
                json.getString("userId"),
                json.getString("username"),
                json.getString("text"),
                json.getLong("timestamp")
            )
            "pong"         -> { /* ignore */ }
        }
    }

    private fun scheduleReconnect(url: String) {
        if (!shouldReconnect) return
        scope.launch {
            Log.d(TAG, "Reconnecting in ${reconnectDelay}ms...")
            delay(reconnectDelay)
            reconnectDelay = minOf(reconnectDelay * 2, 30000L)
            if (shouldReconnect) connect(url)
        }
    }

    fun join(channelId: String, userId: String, username: String, kelurahan: String) {
        sendJson(JSONObject().apply {
            put("type", "join")
            put("channelId", channelId)
            put("userId", userId)
            put("username", username)
            put("kelurahan", kelurahan)
        })
    }

    fun sendPttStart() = sendJson(JSONObject().put("type", "ptt_start"))
    fun sendPttEnd()   = sendJson(JSONObject().put("type", "ptt_end"))

    fun sendAudio(audioData: ByteArray) {
        ws?.send(audioData.toByteString())
    }

    fun sendTextMessage(text: String) {
        sendJson(JSONObject().apply {
            put("type", "text_message")
            put("text", text)
        })
    }

    private fun sendJson(json: JSONObject) {
        if (isConnected) ws?.send(json.toString())
    }

    fun disconnect() {
        shouldReconnect = false
        ws?.close(1000, "User disconnected")
    }

    val connected get() = isConnected
}

data class ChannelUser(val userId: String, val username: String, val kelurahan: String)

interface WalkieSocketListener {
    fun onConnected()
    fun onDisconnected()
    fun onError(message: String)
    fun onJoined(channelId: String)
    fun onUserJoined(userId: String, username: String)
    fun onUserLeft(userId: String, username: String)
    fun onChannelUsers(users: List<ChannelUser>)
    fun onPttStart(userId: String, username: String)
    fun onPttEnd(userId: String, username: String)
    fun onAudioReceived(data: ByteArray, userId: String, username: String)
    fun onTextMessage(userId: String, username: String, text: String, timestamp: Long)
}
