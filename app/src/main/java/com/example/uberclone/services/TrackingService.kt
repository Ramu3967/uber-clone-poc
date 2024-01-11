//package com.example.uberclone.services
//
//import android.content.Intent
//import android.location.Location
//import androidx.core.app.NotificationCompat
//import androidx.lifecycle.LifecycleService
//import com.example.uberclone.utils.TaxiConstants
//import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
//import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
//import com.example.uberclone.utils.TaxiConstants.DB_LATITUDE
//import com.example.uberclone.utils.TaxiConstants.DB_LONGITUDE
//import com.example.uberclone.utils.TaxiConstants.DB_ONGOING_REQUESTS
//import com.example.uberclone.utils.TaxiConstants.DB_RIDER_DETAILS
//import com.example.uberclone.utils.TaxiConstants.DB_RIDER_LOCATION
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationCallback
//import com.google.android.gms.location.LocationResult
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.DataSnapshot
//import com.google.firebase.database.DatabaseError
//import com.google.firebase.database.DatabaseReference
//import com.google.firebase.database.FirebaseDatabase
//import com.google.firebase.database.ValueEventListener
//import dagger.hilt.android.AndroidEntryPoint
//import javax.inject.Inject
//
//class TrackingService : LifecycleService() {
//
////    @Inject
////    lateinit var fusedLocationClient: FusedLocationProviderClient
////    @Inject
////    lateinit var baseNotificationBuilder: NotificationCompat.Builder
//
//    private lateinit var mOngoingReqRef: DatabaseReference
//
//    private val locationCallback= object : LocationCallback() {
//        override fun onLocationResult(result: LocationResult) {
//            super.onLocationResult(result)
//            // update driver's location on FB
//            updateDriverLocationOnFirebase(result.lastLocation)
//        }
//    }
//
//    private fun updateDriverLocationOnFirebase(location: Location?) {
//        location?.let {loc ->
//            auth.currentUser?.let {driver->
//                val ongoingRef = mOngoingReqRef.child(driver.uid)
//                val driverLocationRef = ongoingRef.child(DB_DRIVER_DETAILS).child(DB_DRIVER_LOCATION)
//                driverLocationRef.child(DB_LATITUDE).setValue(loc.latitude)
//                driverLocationRef.child(DB_LONGITUDE).setValue(loc.longitude)
//            }
//        }
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        val dbRef = database.reference
//        mOngoingReqRef = dbRef.child(DB_ONGOING_REQUESTS)
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        return super.onStartCommand(intent, flags, startId)
//        // request location updates
//
//        // cancel ride - remove location updates
//
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // remove location updates
//    }
//
//    companion object{
//        const val TAG = "my#TrackingService"
//    }
//}