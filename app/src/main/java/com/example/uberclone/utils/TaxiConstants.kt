package com.example.uberclone.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TaxiConstants  {
    // home fragment
    const val RIDER = "Rider"
    const val DRIVER = "Driver"

    // FB details for the active requests node - rider
    const val DB_ACTIVE_REQUESTS = "activeRequests"
    const val DB_LOCATION = "location"
    const val DB_LATITUDE = "latitude"
    const val DB_LONGITUDE = "longitude"
    const val DB_REQUESTED_AT = "requestedAt"

    // FB details for the users node
    const val USER_TYPE = "userType"
    const val EMAIL = "email"
    const val USERS = "users"

    private const val EARTH_RADIUS = 6371.0 // Earth radius in kilometers

    // Function to calculate distance using Haversine formula
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS * c
    }

    fun calculateDistance(source: LatLng, destination: LatLng): Double {
        return calculateDistance(source.latitude, source.longitude, destination.latitude, destination.longitude)
    }
}

enum class HOMESCREENDIRECTIONS{
    DIR_HOME, DIR_RIDER, DIR_DRIVER
}