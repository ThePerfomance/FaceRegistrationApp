package com.example.faceapp.activity

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.faceapp.R

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val username = intent.getStringExtra("username")
        val textViewUsername = findViewById<TextView>(R.id.textViewUsername)
        textViewUsername.text = username
    }
}