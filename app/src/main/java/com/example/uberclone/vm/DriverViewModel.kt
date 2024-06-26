package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.RideStatus
import com.example.uberclone.utils.TaxiConstants.DB_ACCEPTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_ID
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_END_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_ONGOING_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_ID
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_RIDE_STATUS
import com.example.uberclone.utils.TaxiConstants.DB_START_LOCATION
import com.example.uberclone.utils.TaxiConstants.DELIMITER
import com.example.uberclone.utils.TaxiRequest
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DriverViewModel@Inject constructor(
    private var auth: FirebaseAuth,
    private var database: FirebaseDatabase
): ViewModel() {

    private val _mUserRequestsLV = MutableLiveData(emptyList<TaxiRequest>())
    val mUserRequestsLV: LiveData<List<TaxiRequest>>
        get() = _mUserRequestsLV

    private val _navigationLV = MutableLiveData<LatLng?>(null)
    val mNavigationLV
    get() = _navigationLV

    private var isListeningForDb = false
    private var isRequestAccepted = false

    private lateinit var mActiveReqRef: DatabaseReference
    private lateinit var mOngoingReqRef: DatabaseReference

    // rearrange the code -> similar functions together

    private val mActiveReqDbListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "mActiveReqListener_onDataChange: data changed}")
            if (snapshot.exists()) {
                val results = mutableListOf<TaxiRequest>()
                for (uidSnapshot in snapshot.children) {
                    if(uidSnapshot.child(DB_RIDE_STATUS).getValue(String::class.java)==RideStatus.PENDING.name)
                        results.add(TaxiRequest.fromActiveReqSnapshot(uidSnapshot))
                }
                _mUserRequestsLV.value= results
            } else {
                Log.d(TAG, "mActiveReqDbListener_onDataChange: No records of active requests found")
                _mUserRequestsLV.value = emptyList()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.d(TAG, "mActiveReqDbListener_onCancelled: ${error.message}")
        }
    }

    /**
     * stores ongoing requests
     * @param taxiRequest : selected request by the driver
     * @param driverLocation : driver's last known location
     */
    fun acceptTaxiRequest(taxiRequest: TaxiRequest, driverLocation: LatLng){
        auth.currentUser?.let {driver ->
            val driverId = driver.uid
            saveOngoingRequests(driverId, driverLocation, taxiRequest)
            updateRiderRequestEnROute(driver, taxiRequest)
        }
    }

    private fun saveOngoingRequests(driverId: String, driverLocation: LatLng, taxiRequest: TaxiRequest){
        val ongoingReqRef = mOngoingReqRef.child(driverId)
        val driverRef = ongoingReqRef.child(DB_DRIVER_DETAILS)
        val riderRef = ongoingReqRef.child(DB_RIDER_DETAILS)

        val dLoc = "${driverLocation.latitude}$DELIMITER${driverLocation.longitude}"
        val riderStartLoc = "${taxiRequest.startLocation.latitude}$DELIMITER${taxiRequest.startLocation.longitude}"
        val riderEndLoc = "${taxiRequest.endLocation.latitude}$DELIMITER${taxiRequest.endLocation.longitude}"

        val updates = mapOf(
            "${driverRef.key}/$DB_DRIVER_LOCATION" to dLoc,
            "${driverRef.key}/$DB_ACCEPTED_AT" to System.currentTimeMillis(),
            "${riderRef.key}/$DB_RIDER_ID" to taxiRequest.uid,
            "${riderRef.key}/$DB_START_LOCATION" to riderStartLoc,
            "${riderRef.key}/$DB_END_LOCATION" to riderEndLoc
        )

        mOngoingReqRef.child(driverId).updateChildren(updates)

        isRequestAccepted = true
    }

    private fun updateRiderRequestEnROute(driver: FirebaseUser, taxiRequest: TaxiRequest) {
        val riderRequestRef = database.reference.child("$DB_RIDER_REQUESTS/${taxiRequest.uid}")
        val children = mapOf(
            DB_DRIVER_ID to driver.uid,
            DB_RIDE_STATUS to RideStatus.EN_ROUTE
        )
        riderRequestRef.updateChildren(children)
    }

    fun listenForDbChanges(){
        if(!isListeningForDb) {
            isListeningForDb = true
            val dbRef = database.reference
            mActiveReqRef = dbRef.child(DB_RIDER_REQUESTS)
            mActiveReqRef.addValueEventListener(mActiveReqDbListener)

            mOngoingReqRef = dbRef.child(DB_ONGOING_REQUESTS)
        }
    }

    // TODO: implement cancel ride functionality

    override fun onCleared() {
        super.onCleared()
        if(::mActiveReqRef.isInitialized) mActiveReqRef.removeEventListener(mActiveReqDbListener)
        isListeningForDb = false
        isRequestAccepted = false
    }

    // this should happen after the request has been accepted by the driver, else there would be no ongoingRequests node.
    fun updateDriverLocationInOngoingReqToFirebase(driverLocation: LatLng) {
        if(isRequestAccepted){
            val driverDetailsRef = mOngoingReqRef.child(auth.currentUser?.uid!!).child(DB_DRIVER_DETAILS)
            val loc = "${driverLocation.latitude}$DELIMITER${driverLocation.longitude}"
            driverDetailsRef.child(DB_DRIVER_LOCATION).setValue(loc)
        }else{
            Log.e(TAG,"updateDriverLocationInOngoingReqToFirebase: Driver didn't accept any requests yet")
        }
    }

    fun changeRideStatusToEnRouteDestination(taxiRequest: TaxiRequest) {
        // draw polyline to the endLocation (works on real device, so optional)
        // change the rideStatus to EN_ROUTE_DEST in riderRequests
        val rideStatusRef = database.reference.child("$DB_RIDER_REQUESTS/${taxiRequest.uid}/$DB_RIDE_STATUS")
        rideStatusRef.setValue(RideStatus.EN_ROUTE_DEST.name)
    }

    fun changeRideStatusToFinished(taxiRequest: TaxiRequest) {
        val rideStatusRef = database.reference.child("$DB_RIDER_REQUESTS/${taxiRequest.uid}/$DB_RIDE_STATUS")
        rideStatusRef.setValue(RideStatus.FINISHED)
    }

    fun removeFromOngoingRequests() {
        auth.currentUser?.let {
            mOngoingReqRef.child(it.uid).removeValue()
        }
    }

    companion object{
        const val TAG = "my#DriverViewModel"
    }

}