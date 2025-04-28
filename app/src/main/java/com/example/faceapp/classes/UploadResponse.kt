package com.example.faceapp.classes

import com.squareup.moshi.Json

data class UploadResponse(
    val status: String,
    val message: String,
    val token: String?, // Токен может быть null, если не авторизован
    val user: User?,
    @Json(name = "is_registered") val isRegistered: Boolean
)

data class User(
    val id: Int,
    val username: String,
    @Json(name = "face_id") val faceId: String
)