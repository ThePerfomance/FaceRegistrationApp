package com.example.faceapp.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.faceapp.api.RetrofitInstance
import com.example.faceapp.classes.UploadResponse
import com.example.faceapp.databinding.ActivityCameraBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Запрос разрешений камеры
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        binding.cameraCaptureButton.setOnClickListener {
            takePhoto()
            disableCaptureButton()
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Проверка, что PreviewView не является null
            if (binding.viewFinder == null) {
                Log.e(TAG, "PreviewView не найден в макете")
                return@addListener
            }

            // Настройка предварительного просмотра
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Настройка захвата изображения
            imageCapture = ImageCapture.Builder().build()

            // Выбор фронтальной камеры
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Ошибка при настройке камеры", exc)
                Toast.makeText(this, "Не удалось запустить камеру", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = File(externalMediaDirs.firstOrNull(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Ошибка при съемке фото: ${exc.message}", exc)
                    Toast.makeText(this@CameraActivity, "Ошибка при съемке фото", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    cropImage(savedUri)
                }
            }
        )
    }

    private fun cropImage(uri: Uri) {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val croppedBitmap = getCroppedBitmap(bitmap)

        saveCroppedBitmap(croppedBitmap) { croppedUri ->
            if (croppedUri != null) {
                sendPhotoToServer(croppedUri)
            } else {
                Toast.makeText(this, "Ошибка при обрезке фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCroppedBitmap(bitmap: Bitmap): Bitmap {
        // Определяем размеры овала
        val ovalWidth = (bitmap.width * 0.45f).toInt()
        val ovalHeight = (bitmap.height * 0.45f).toInt()

        // Создаем новый Bitmap с размерами овала
        val output = Bitmap.createBitmap(ovalWidth, ovalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Создаем овальную область
        val ovalPath = Path()
        ovalPath.addOval(0f, 0f, ovalWidth.toFloat(), ovalHeight.toFloat(), Path.Direction.CW)
        canvas.clipPath(ovalPath)

        // Рассчитываем координаты для отрисовки исходного изображения
        val leftOffset = (bitmap.width - ovalWidth) / 2
        val topOffset = (bitmap.height - ovalHeight) / 2

        // Рисуем исходное изображение на холсте с учетом клиппирования
        canvas.drawBitmap(bitmap, -leftOffset.toFloat(), -topOffset.toFloat(), null)
        return output
    }

    private fun saveCroppedBitmap(bitmap: Bitmap, callback: (Uri?) -> Unit) {
        val photoFile = File(externalMediaDirs.firstOrNull(), "${System.currentTimeMillis()}_cropped.jpg")
        try {
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            callback(Uri.fromFile(photoFile))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении обрезанного фото", e)
            callback(null)
        }
    }

    private fun sendPhotoToServer(uri: Uri) {
        val file = File(uri.path!!)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        val call = RetrofitInstance.api.uploadImage(body)
        call.enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                enableCaptureButton() // Включаем кнопку после завершения
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    if (uploadResponse != null) {
                        handleServerResponse(uploadResponse)
                    } else {
                        Toast.makeText(this@CameraActivity, "Неверный формат ответа сервера", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CameraActivity, "Ошибка сервера", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                Log.e(TAG, "Ошибка сети", t)
                Toast.makeText(this@CameraActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                enableCaptureButton() // Включаем кнопку в случае ошибки
            }
        })
    }

    private fun handleServerResponse(response: UploadResponse) {
        when (response.status) {
            "success" -> {
                if (response.isRegistered == true) {
                    // Пользователь авторизован
                    val username = response.user?.username
                    val token = response.token
                    if (username != null && token != null) {
                        Toast.makeText(this, "Авторизация успешна", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.putExtra("username", username)
                        intent.putExtra("token", token) // Передаем токен в ProfileActivity
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Ошибка: Неполные данные пользователя", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Новый пользователь
                    Toast.makeText(this, "Новый пользователь", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, RegistrationActivity::class.java)
                    startActivity(intent)
                }
            }
            "FACE_NOT_DETECTED" -> {
                // Лицо не найдено
                Toast.makeText(this, "Лицо не найдено, повторите попытку", Toast.LENGTH_SHORT).show()
            }
            "pending_registration" -> {
                // Пользователь не найден, требуется регистрация
                    Toast.makeText(this, "Пользователь не найден. Вход в приложение запрещен", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Неизвестный статус
                Toast.makeText(this, "Ошибка: ${response.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun disableCaptureButton() {
        // Делаем кнопку неактивной
        binding.cameraCaptureButton.isEnabled = false
        binding.cameraCaptureButton.text = "Загрузка..."

        // Показываем ProgressBar
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun enableCaptureButton() {
        // Возвращаем кнопке активное состояние
        binding.cameraCaptureButton.isEnabled = true
        binding.cameraCaptureButton.text = "Сделать фото"

        // Скрываем ProgressBar
        binding.progressBar.visibility = View.GONE
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCamera()
        } else {
            Toast.makeText(this, "Разрешения не предоставлены", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}