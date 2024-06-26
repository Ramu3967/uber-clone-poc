package com.example.uberclone.utils

import android.view.View
import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TaxiConstants  {
    // home fragment
    const val RIDER = "Rider"
    const val DRIVER = "Driver"

    // Location constants
    const val LOCATION_INTERVAL=4000L
    const val LOCATION_FASTEST_INTERVAL = 5000L
    const val LOCATION_MAX_WAIT_TIME = 10000L

    const val LOCATION_INTERVAL_RIDER=10000L
    const val LOCATION_FASTEST_INTERVAL_RIDER = 10000L
    const val LOCATION_MAX_WAIT_TIME_RIDER = 20000L

    // FB details for the active requests node - rider
    const val DB_RIDER_REQUESTS = "riderRequests"
    const val DB_START_LOCATION = "startLocation"
    const val DB_END_LOCATION = "endLocation"
    const val DB_REQUESTED_AT = "requestedAt"
    const val DB_RIDE_STATUS = "rideStatus"
    const val DB_DRIVER_ID = "driverId"
    const val DB_EMPTY_FIELD = ""


    // FB details for the ongoing requests node - driver
    const val DB_ONGOING_REQUESTS = "ongoingRequests"
    const val DB_DRIVER_DETAILS = "driverDetails"
    const val DB_DRIVER_LOCATION = "driverLocation"
    const val DB_ACCEPTED_AT = "acceptedAt"
    const val DB_RIDER_DETAILS = "riderDetails"
    const val DB_RIDER_ID = "riderId"
    const val DB_RIDER_LOCATION = "riderLocation"

    // FB details for the users node
    const val USER_TYPE = "userType"
    const val EMAIL = "email"
    const val USERS = "users"

    // finished rides
    const val DB_FINISHED_REQUESTS = "finishedRequests"
    const val DB_FINISHED_STATUS = "status"
    const val DB_CANCELED_REASON = "reason"
    const val DB_CANCELED_BY = "canceledBy"
    const val DB_DROP_OFF = "dropOff"

    const val UNIT_KM = " KM"
    const val DELIMITER = ","

    // Rider Scenario
    const val MAP_ANIMATION_DURATION = 1000

    // kilometers
    private const val EARTH_RADIUS = 6371.0
    const val DIST_NEAR_BY = 1.5
    const val DIST_ARRIVAL_MIN = 0.0
    const val DIST_ARRIVAL_MAX = 0.1

    // Function to calculate distance using Haversine formula
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distance = EARTH_RADIUS * c
        return String.format("%.2f", distance).toDouble()
    }

    fun calculateDistance(source: LatLng, destination: LatLng): Double {
        return calculateDistance(source.latitude, source.longitude, destination.latitude, destination.longitude)
    }

    fun View.show(){ visibility = View.VISIBLE}
    fun View.hide(){ visibility = View.INVISIBLE}
    fun View.remove(){ visibility = View.GONE}
}

enum class HomeScreenDirections{
    DIR_HOME, DIR_RIDER, DIR_DRIVER
}

enum class RideStatus{
    PENDING, EN_ROUTE, FINISHED, CANCELLED_ONGOING_RIDE, EN_ROUTE_DEST
}

sealed class RiS{
    object Pending: RiS()
    object EnRoute: RiS()
    object Finished: RiS()
    class CancelledOngoingRide(val msg: String): RiS()
}