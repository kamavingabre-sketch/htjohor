package id.go.medanjohor.walkietalkie.utils

import android.content.Context
import java.util.UUID

class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("walkie_prefs", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(v) = prefs.edit().putString("username", v).apply()

    var kelurahan: String
        get() = prefs.getString("kelurahan", "") ?: ""
        set(v) = prefs.edit().putString("kelurahan", v).apply()

    var lastChannel: String
        get() = prefs.getString("last_channel", "Umum") ?: "Umum"
        set(v) = prefs.edit().putString("last_channel", v).apply()

    val userId: String by lazy {
        prefs.getString("user_id", null) ?: run {
            val id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("user_id", id).apply()
            id
        }
    }

    val isLoggedIn get() = username.isNotBlank()

    fun logout() {
        prefs.edit()
            .remove("username")
            .remove("kelurahan")
            .apply()
    }
}

val KELURAHAN_LIST = listOf(
    "Kwala Bekala",
    "Gedung Johor",
    "Suka Maju",
    "Pangkalan Masyhur",
    "Titi Kuning",
    "Kedai Durian"
)

val CHANNEL_LIST = listOf(
    "Umum",
    "Darurat",
    "Petugas Lapangan",
    "Kebersihan",
    "Keamanan",
    "Administrasi"
)
