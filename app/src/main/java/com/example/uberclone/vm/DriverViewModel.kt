package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.TaxiConstants
import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiRequest
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
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

    private lateinit var mActiveReqRef: DatabaseReference

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
                Log.d(TAG, "onDataChange: No records of active requests found")
                _mUserRequestsLV.value = emptyList()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.d(TAG, "onCancelled: ${error.message}")
        }
    }

    fun listenForDbChanges(){
        val dbRef = database.reference
        mActiveReqRef = dbRef.child(TaxiConstants.DB_ACTIVE_REQUESTS)
        mActiveReqRef.addValueEventListener(mActiveReqDbListener)
    }

    override fun onCleared() {
        super.onCleared()
        if(::mActiveReqRef.isInitialized)
            mActiveReqRef.removeEventListener(mActiveReqDbListener)
    }

    companion object{
        const val TAG = "my#DriverViewModel"
    }

}