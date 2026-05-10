package id.go.medanjohor.walkietalkie.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import id.go.medanjohor.walkietalkie.R
import id.go.medanjohor.walkietalkie.utils.KELURAHAN_LIST
import id.go.medanjohor.walkietalkie.utils.UserPrefs

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        prefs = UserPrefs(this)

        val etName = findViewById<TextInputEditText>(R.id.etUsername)
        val ddKelurahan = findViewById<AutoCompleteTextView>(R.id.ddKelurahan)
        val btnMasuk = findViewById<MaterialButton>(R.id.btnMasuk)

        val adapter = ArrayAdapter(this, R.layout.item_dropdown, KELURAHAN_LIST)
        ddKelurahan.setAdapter(adapter)

        btnMasuk.setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val kel  = ddKelurahan.text?.toString()?.trim() ?: ""
            if (name.length < 3) {
                Snackbar.make(it, "Nama minimal 3 karakter", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (kel.isBlank()) {
                Snackbar.make(it, "Pilih kelurahan terlebih dahulu", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.username  = name
            prefs.kelurahan = kel
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
