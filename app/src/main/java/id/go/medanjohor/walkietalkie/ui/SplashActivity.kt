package id.go.medanjohor.walkietalkie.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import id.go.medanjohor.walkietalkie.utils.UserPrefs

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = UserPrefs(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val target = if (prefs.isLoggedIn) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this, target))
            finish()
        }, 1800)
    }
}
