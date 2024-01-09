package com.example.uberclone.utils

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

object LocationUtils {

    fun GoogleMap?.updateCurrentLocationMarker(latLng: LatLng) {
        this?.apply {
            // clearing the map
            clear()
            // adding a marker for the current location
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("Current Location")
            addMarker(markerOptions)
            // move the camera to the current location
            moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }
}