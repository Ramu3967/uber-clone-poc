package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.TaxiConstants.DB_ACTIVE_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_ONGOING_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_ID
import com.example.uberclone.utils.TaxiConstants.DELIMITER
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

    var mDriverUpdatesLV : MutableLiveData<LatLng?> = MutableLiveData(null)

    private var mActiveReqRef: DatabaseReference
    private lateinit var mOngoingReqRef: DatabaseReference
    private var acceptedDriverDbRef: DatabaseReference? = null


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

                val matchedReq = ongoingReqSnapshot.children.firstOrNull {
                    it.child(DB_RIDER_DETAILS).child(DB_RIDER_ID)
                        .getValue(String::class.java) == user.uid
                }

                matchedReq?.ref.let {
                    acceptedDriverDbRef = it
                    setDriverDbLocationListener()
                }

                mOngoingReqRef.removeEventListener(this)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "onCancelled: Rider has no requests ")
            }

        })
    }

    fun setDriverDbLocationListener(){
        acceptedDriverDbRef?.let {
            val driverLocationRef = it.child(DB_DRIVER_DETAILS).child(DB_DRIVER_LOCATION)
            driverLocationRef.addValueEventListener(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val (lat,lon) = snapshot.getValue(String::class.java)!!.split(DELIMITER).map{ st -> st.toDouble() }
                    mDriverUpdatesLV.value= LatLng(lat,lon)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "onCancelled: failed to add eventListener ${error.message}", )
                    mDriverUpdatesLV.value = null
                }
            })

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
        acceptedDriverDbRef = null
    }

    companion object{
        const val TAG = "my#RiderViewModel"
    }

}