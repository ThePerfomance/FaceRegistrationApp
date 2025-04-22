package com.example.faceapp.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
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
import androidx.core.graphics.createBitmap
import com.example.faceapp.api.RetrofitInstance
import com.example.faceapp.classes.UploadResponse

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

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

        binding.cameraCaptureButton.setOnClickListener { takePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Не удалось запустить камеру", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = File(externalMediaDirs.firstOrNull(),
            "${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(this@CameraActivity, "Ошибка при снятии фото", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    cropImage(savedUri)
                }
            })
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
        val ovalWidth = (bitmap.width * 0.45f).toInt() // Ширина овала
        val ovalHeight = (bitmap.height * 0.45f).toInt() // Высота овала

        // Создаем новый Bitmap с размерами овала
        val output = createBitmap(ovalWidth, ovalHeight)
        val canvas = Canvas(output)

        // Создаем овальную область
        val ovalPath = Path()
        ovalPath.addOval(0f, 0f, ovalWidth.toFloat(), ovalHeight.toFloat(), Path.Direction.CW)
        ovalPath.close()

        // Очищаем холст (делаем его полностью прозрачным)
        canvas.drawColor(Color.TRANSPARENT)

        // Устанавливаем режим клиппирования (обрезки) по овальной области
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

            // Сохраняем в галерею для отладки
            saveToGallery(bitmap)

            callback(Uri.fromFile(photoFile))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении обрезанного фото", e)
            callback(null)
        }
    }
    private fun saveToGallery(bitmap: Bitmap) {
        val savedImageURL = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "Cropped_Photo_${System.currentTimeMillis()}",
            "Обрезанное фото"
        )

        if (savedImageURL != null) {
            Toast.makeText(this, "Фото сохранено в галерею", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ошибка при сохранении в галерею", Toast.LENGTH_SHORT).show()
        }
    }
    private fun sendPhotoToServer(uri: Uri) {
        val file = File(uri.path!!)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val call = RetrofitInstance.api.uploadImage(body)
        call.enqueue(object : Callback<UploadResponse> {
            override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    if (uploadResponse != null) {
                        handleServerResponse(uploadResponse.status, uploadResponse.username)
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
            }
        })
    }

    private fun handleServerResponse(status: String, username: String?) {
        when (status) {
            "FACE_NOT_FOUND" -> Toast.makeText(this, "Лицо не найдено", Toast.LENGTH_SHORT).show()
            "NEW_USER" -> {
                val intent = Intent(this, RegistrationActivity::class.java)
                startActivity(intent)
            }
            "AUTHORIZED" -> {
                if (username != null) {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("username", username)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Имя пользователя не получено", Toast.LENGTH_SHORT).show()
                }
            }
            else -> Toast.makeText(this, "Неизвестный статус", Toast.LENGTH_SHORT).show()
        }
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