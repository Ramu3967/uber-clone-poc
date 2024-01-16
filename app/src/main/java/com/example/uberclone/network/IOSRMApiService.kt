package com.example.uberclone.network

import com.example.uberclone.utils.OSRMResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface IOSRMApiService {

    @GET("/route/v1/car/{coordinates}")
    suspend fun getRoutes(
        @Path("coordinates") coordinates: String,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full"
    ): Response<OSRMResponse>
}