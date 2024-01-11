package com.example.uberclone.utils

import com.example.uberclone.utils.TaxiConstants.DB_LATITUDE
import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_LONGITUDE
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot

data class TaxiRequest(
    val location: LatLng,
    val uid: String,
    val requestAt: Long
){
    companion object {
        fun fromSnapshot(uidSnapshot: DataSnapshot): TaxiRequest {
            val uid = uidSnapshot.key!!
            val locationSnapshot = uidSnapshot.child(DB_LOCATION)
            val latitude = locationSnapshot.child(DB_LATITUDE).getValue(Double::class.java)!!
            val longitude = locationSnapshot.child(DB_LONGITUDE).getValue(Double::class.java)!!
            val location = LatLng(latitude, longitude)
            val requestAt = uidSnapshot.child(DB_REQUESTED_AT).getValue(Long::class.java) ?: 0L
            return TaxiRequest(location, uid, requestAt)
        }
    }
}
