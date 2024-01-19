package com.example.uberclone.utils

import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_ID
import com.example.uberclone.utils.TaxiConstants.DB_EMPTY_FIELD
import com.example.uberclone.utils.TaxiConstants.DB_END_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_START_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_RIDE_STATUS
import com.example.uberclone.utils.TaxiConstants.DELIMITER
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.getValue

data class TaxiRequest(
    val startLocation: LatLng,
    val endLocation: LatLng,
    val uid: String,
    val requestAt: Long,
    val status: RideStatus,
    val driverId: String
){
    companion object {
        fun fromActiveReqSnapshot(uidSnapshot: DataSnapshot): TaxiRequest {
            // todo use only one get call to deserialize
            val uid = uidSnapshot.key!!
            val startLocationSnapshot = uidSnapshot.child(DB_START_LOCATION)
            val (lat1,lon1) = startLocationSnapshot.getValue(String::class.java)!!.split(DELIMITER).map{ it.toDouble() }
            val startLoc = LatLng(lat1, lon1)
            val endLocationSnapshot = uidSnapshot.child(DB_END_LOCATION)
            val (lat2,lon2) = endLocationSnapshot.getValue(String::class.java)!!.split(DELIMITER).map{ it.toDouble() }
            val endLoc = LatLng(lat2, lon2)
            val requestAt = uidSnapshot.child(DB_REQUESTED_AT).getValue(Long::class.java) ?: 0L
            val rideStatus = uidSnapshot.child(DB_RIDE_STATUS).getValue(RideStatus::class.java)!!
            val driverId = uidSnapshot.child(DB_DRIVER_ID).getValue(String::class.java) ?: DB_EMPTY_FIELD
            return TaxiRequest(startLoc, endLoc,uid, requestAt, rideStatus, driverId)
        }
    }
}
