package com.example.atsattendance.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface AttendanceApiService {
    @GET("daily_report/{date}")
    fun getDailyReport(@Path("date") date: String): Call<ResponseBody>

    @GET("weekly_report/{year}/{week}")
    fun getWeeklyReport(@Path("year") year: String, @Path("week") week: String): Call<ResponseBody>
}