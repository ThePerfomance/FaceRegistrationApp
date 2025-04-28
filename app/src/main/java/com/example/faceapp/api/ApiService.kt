package com.example.faceapp.api

import com.example.faceapp.classes.UploadResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("auth/face/")
    fun uploadImage(@Part file: MultipartBody.Part): Call<UploadResponse>
}