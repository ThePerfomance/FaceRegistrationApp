package com.example.faceapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLogin = findViewById<Button>(R.id.btn_login)
        btnLogin.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
    private fun handleServerResponse(response: String) {
        when (response) {
            "FACE_NOT_FOUND" -> Toast.makeText(this, "Лицо не найдено", Toast.LENGTH_SHORT).show()
            "NEW_USER" -> {
                val intent = Intent(this, RegistrationActivity::class.java)
                startActivity(intent)
            }
            else -> {
                val intent = Intent(this, ProfileActivity::class.java)
                intent.putExtra("username", response)
                startActivity(intent)
            }
        }
    }
}