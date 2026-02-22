package com.example.atsattendance.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://your-pi-ip-address:5000/"  // Replace with your Raspberry Pi IP or Ngrok URL

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: AttendanceApiService = retrofit.create(AttendanceApiService::class.java)
}