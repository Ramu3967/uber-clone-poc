package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.DriverUpdates
import com.example.uberclone.utils.TaxiConstants.DB_ACTIVE_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_ONGOING_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_ID
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DELIMITER
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MAX
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MIN
import com.example.uberclone.utils.TaxiConstants.DIST_NEAR_BY
import com.example.uberclone.utils.TaxiConstants.calculateDistance
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
class RiderViewModel@Inject constructor(
    private var auth: FirebaseAuth,
    private var database: FirebaseDatabase
): ViewModel() {

    private val _mRequestStateLV: MutableLiveData<Boolean?> = MutableLiveData(null)
    val mRequestStateLV: LiveData<Boolean?>
        get() = _mRequestStateLV

    var mDriverUpdatesLV = MutableLiveData(DriverUpdates())


    private var mActiveReqRef: DatabaseReference
    private lateinit var mOngoingReqRef: DatabaseReference
    private var mAcceptedDriverDbRef: DatabaseReference? = null
    private var mRiderLocation: LatLng? = null

    private var isRiderFirstNotification = false
    private var driverNearbyMessageSent = false

    private val mActiveReqListener = object : ValueEventListener{
        override fun onDataChange(activeReqSnapshot: DataSnapshot) {
            Log.d(TAG, "mActiveReqListener_onDataChange: data changed}")
            auth.currentUser?.let { user ->
                if(activeReqSnapshot.exists()){
                    val isReqExists = activeReqSnapshot.hasChild(user.uid)
                    _mRequestStateLV.value = isReqExists
                    // when an active req is deleted and could be found in onGoing Req -> Driver just accepted this req
                    /*
                     disable the 'call taxi' button and start updating the driver's location
                      */
                    if(isReqExists){
                        Log.d(TAG, "onDataChange: Rider's request is still in activeRequests. No driver has confirmed it yet")
                    }
                    else{
                        // search in ongoing requests
                        checkRiderInOngoingRequests(user)
                    }
                }
                else {
                    _mRequestStateLV.value = false
                    // case where the current rider's was the only one active request that got deleted by accepting the req by driver.
                    checkRiderInOngoingRequests(user)
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.d(TAG, "mActiveReqListener_onCancelled: data cancelled}")
        }
    }

    private fun checkRiderInOngoingRequests(user: FirebaseUser) {
        mOngoingReqRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ongoingReqSnapshot: DataSnapshot) {

                val matchedDriverReq = ongoingReqSnapshot.children.firstOrNull {
                    it.child(DB_RIDER_DETAILS).child(DB_RIDER_ID).getValue(String::class.java) == user.uid
                }

                matchedDriverReq?.let {
                    // driver has accepted the request
                    isRiderFirstNotification = true
                    mAcceptedDriverDbRef = it.ref
                    val (lat,lon) = it.child(DB_RIDER_DETAILS).child(DB_RIDER_LOCATION).getValue(String::class.java)!!.split(DELIMITER).map{ st -> st.toDouble() }
                    mRiderLocation = LatLng(lat,lon)
                    setDriverDbLocationListener()
                }

                mOngoingReqRef.removeEventListener(this)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "onCancelled: Rider has no requests ")
            }

        })
    }

    private val driverLocationRefListener = object: ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            val (lat,lon) = snapshot.getValue(String::class.java)!!.split(DELIMITER).map{ st -> st.toDouble() }
            val driverLocation = LatLng(lat,lon)
            if(isRiderFirstNotification) {
                mDriverUpdatesLV.value = DriverUpdates(location = driverLocation, msg = "The driver is on the way")
                isRiderFirstNotification = false
            }
            else {
                // todo: calculate distances and update the messages accordingly here - ex if the dist is <3KM notify the rider about the driver
                val distanceRiderDriver = calculateDistance(mRiderLocation!!, driverLocation)
                mDriverUpdatesLV.value = when{
                    distanceRiderDriver in DIST_ARRIVAL_MIN..DIST_ARRIVAL_MAX -> {  DriverUpdates(location = driverLocation, msg= null, driverReached = true)  }
                    distanceRiderDriver < DIST_NEAR_BY  && !driverNearbyMessageSent-> {
                        driverNearbyMessageSent = true
                        DriverUpdates(location = driverLocation, msg = "Driver is nearby, please get ready to ride")
                    }
                    else -> {DriverUpdates(location = driverLocation, msg = null)}
                }
            }

        }

        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "onCancelled: failed to add eventListener ${error.message}", )
            mDriverUpdatesLV.value = DriverUpdates()
        }
    }

    fun setDriverDbLocationListener(){
        mAcceptedDriverDbRef?.let {
            val driverLocationRef = it.child(DB_DRIVER_DETAILS).child(DB_DRIVER_LOCATION)
            driverLocationRef.addValueEventListener(driverLocationRefListener)
            driverNearbyMessageSent = false
        } ?: Log.e(TAG, "setDriverSnapshotListener: no accepted driver found, so no listener was set")
    }

    init {
        val dbRef = database.reference
        mActiveReqRef = dbRef.child(DB_ACTIVE_REQUESTS)
        mActiveReqRef.addValueEventListener(mActiveReqListener)

        mOngoingReqRef = dbRef.child(DB_ONGOING_REQUESTS) // what if there's no ongoing req, how'd you search for the rider id?
    }

    fun sendTaxiRequest(latLng: LatLng){
        auth.currentUser?.let {user ->
            val dbRef = database.reference
            val activeReqRef = dbRef.child(DB_ACTIVE_REQUESTS).child(user.uid)

            val loc = "${latLng.latitude}${DELIMITER}${latLng.longitude}"
            activeReqRef.child(DB_LOCATION).setValue(loc)
            activeReqRef.child(DB_REQUESTED_AT).setValue(System.currentTimeMillis())

        } ?: Log.d(TAG,"taxiRequest: unable to send req as there's no user available")
    }

    fun cancelTaxiRequest(){
        auth.currentUser?.let { user ->
            val dbRef = database.reference
            val activeReqRef = dbRef.child(DB_ACTIVE_REQUESTS).child(user.uid)
            activeReqRef.removeValue()
        }
        Log.d(TAG, "cancelTaxiRequest: taxi cancelled")
    }

    override fun onCleared() {
        super.onCleared()
        mActiveReqRef.removeEventListener(mActiveReqListener)
        stopDriverUpdates()
    }

    fun stopDriverUpdates() {
        mAcceptedDriverDbRef?.child(DB_DRIVER_DETAILS)?.child(DB_DRIVER_LOCATION)
            ?.removeEventListener(driverLocationRefListener)
        mAcceptedDriverDbRef = null
        isRiderFirstNotification = false
    }

    companion object{
        const val TAG = "my#RiderViewModel"
    }

}