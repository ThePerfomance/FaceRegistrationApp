package com.example.faceapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.faceapp.databinding.ActivityRegistrationBinding

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val username = binding.editTextUsername.text.toString()
            registerUser(username)
        }
    }

    private fun registerUser(username: String) {
        // Здесь вы можете использовать библиотеку для отправки данных на сервер, например Retrofit или OkHttp
        // После получения ответа от сервера:
        handleRegistrationResponse("USERNAME_TAKEN") // Пример ответа от сервера
    }

    private fun handleRegistrationResponse(response: String) {
        when (response) {
            "USERNAME_TAKEN" -> Toast.makeText(this, "Имя уже занято", Toast.LENGTH_SHORT).show()
            else -> {
                Toast.makeText(this, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}