package com.example.uberclone.utils

import com.google.android.gms.maps.model.LatLng

data class DriverUpdates(
    val driverId: String? = null,
    val location: LatLng? = null,
    val msg: String? = null,
    val driverReached: Boolean = false
)
