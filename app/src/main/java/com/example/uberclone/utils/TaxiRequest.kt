package com.example.uberclone.utils

import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiConstants.DELIMITER
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot

data class TaxiRequest(
    val location: LatLng,
    val uid: String,
    val requestAt: Long
){
    companion object {
        fun fromActiveReqSnapshot(uidSnapshot: DataSnapshot): TaxiRequest {
            val uid = uidSnapshot.key!!
            val locationSnapshot = uidSnapshot.child(DB_LOCATION)
            val (lat,lon) = locationSnapshot.getValue(String::class.java)!!.split(DELIMITER).map{ it.toDouble() }
            val location = LatLng(lat, lon)
            val requestAt = uidSnapshot.child(DB_REQUESTED_AT).getValue(Long::class.java) ?: 0L
            return TaxiRequest(location, uid, requestAt)
        }
    }
}
