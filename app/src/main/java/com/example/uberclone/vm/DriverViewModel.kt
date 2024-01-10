package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.TaxiConstants
import com.example.uberclone.utils.TaxiConstants.DB_ACCEPTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_ID
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_LOCATION
import com.example.uberclone.utils.TaxiRequest
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.childEvents
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

    private var isListeningForDb = false

    private lateinit var mActiveReqRef: DatabaseReference
    private lateinit var mOngoingReqRef: DatabaseReference

    private val mActiveReqDbListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "mActiveReqListener_onDataChange: data changed}")
            if (snapshot.exists()) {
                val results = mutableListOf<TaxiRequest>()
                for (requestSnapshot in snapshot.children) {
                    results.add(TaxiRequest.fromSnapshot(requestSnapshot))
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
     * searches for ongoing requests of the current driver.
     */
    private val mOngoingReqDbListener = object: ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "mOngoingReqDbListener_onDataChange: triggered}")
            if(snapshot.exists()){
                auth.currentUser?.uid?.let { driverId ->
                    if(snapshot.hasChild(driverId)){
                        // removing the active req associated with this rider
                        val acceptedReq = snapshot.child(driverId)
                        val riderId = acceptedReq.child("riderDetails").child("riderId").getValue(String::class.java)
                        riderId?.let {
                        Log.d(TAG, "mOngoingReqDbListener_onDataChange: The driver($driverId) has an on going request with the user($riderId)")
                            deleteActiveRequestWithId(activeReqId = it)
                        }
                            ?: Log.e(TAG,"unable to delete the rider - No such rider")
                    }else{
                        Log.d(TAG, "mOngoingReqDbListener_onDataChange: This driver doesn't have any on going requests")
                    }
                }
            }else{
                Log.d(TAG, "mOngoingReqDbListener_onDataChange: No records of ongoing requests found")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            TODO("Not yet implemented")
        }
    }

    private fun deleteActiveRequestWithId(activeReqId: String) {
        mActiveReqRef.child(activeReqId).addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.exists()){
                    // this triggers the activeReq listeners and updates all the drivers who are logged in
                     mActiveReqRef.child(activeReqId).removeValue()
                    Log.d(TAG, "deleteActiveRequestWithId_onDataChange: request with ID: $activeReqId deleted ")
                }
            }

            // called if there is an exception on accessing this child
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "deleteActiveRequestWithId_onCancelled: Child with ID $activeReqId does not exist", )
            }
        })
    }

    /**
     * stores ongoing requests
     * @param taxiRequest : selected request by the driver
     * @param driverLocation : driver's last known location
     */
    fun acceptTaxiRequest(taxiRequest: TaxiRequest, driverLocation: LatLng){
        auth.currentUser?.let {driver ->
            val riderId = taxiRequest.uid
            val driverId = driver.uid
            val riderLocation = taxiRequest.location
            saveOngoingRequests(driverId, driverLocation, riderId, riderLocation)
        }
    }

    private fun saveOngoingRequests(driverId: String, driverLocation: LatLng, riderId: String, riderLocation: LatLng){
        val ongoingReqRef = mOngoingReqRef.child(driverId)
        val driverRef = ongoingReqRef.child(DB_DRIVER_DETAILS)
        driverRef.child(DB_DRIVER_LOCATION).setValue(driverLocation)
        driverRef.child(DB_ACCEPTED_AT).setValue(System.currentTimeMillis())
        val riderRef = ongoingReqRef.child(DB_RIDER_DETAILS)
        riderRef.child(DB_RIDER_ID).setValue(riderId)
        riderRef.child(DB_RIDER_LOCATION).setValue(riderLocation)
    }

    fun listenForDbChanges(){
        if(!isListeningForDb) {
            isListeningForDb = true
            val dbRef = database.reference
            mActiveReqRef = dbRef.child(TaxiConstants.DB_ACTIVE_REQUESTS)
            mActiveReqRef.addValueEventListener(mActiveReqDbListener)

            mOngoingReqRef = dbRef.child("ongoingRequests")
            mOngoingReqRef.addValueEventListener(mOngoingReqDbListener)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if(::mActiveReqRef.isInitialized) mActiveReqRef.removeEventListener(mActiveReqDbListener)
        if(::mOngoingReqRef.isInitialized) mOngoingReqRef.removeEventListener(mOngoingReqDbListener)
        isListeningForDb = false
    }

    companion object{
        const val TAG = "my#DriverViewModel"
    }

}