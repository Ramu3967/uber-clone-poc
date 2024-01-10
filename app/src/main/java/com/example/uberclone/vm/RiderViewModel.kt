package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.TaxiConstants.DB_ACTIVE_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
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
class RiderViewModel@Inject constructor(
    private var auth: FirebaseAuth,
    private var database: FirebaseDatabase
): ViewModel() {

    private val _mRequestStateLV: MutableLiveData<Boolean?> = MutableLiveData(null)
    val mRequestStateLV: LiveData<Boolean?>
        get() = _mRequestStateLV

    private var mActiveReqRef: DatabaseReference

    private val mActiveReqListener = object : ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "mActiveReqListener_onDataChange: data changed}")
            auth.currentUser?.let { user ->
                if(snapshot.exists()){
                    _mRequestStateLV.value = snapshot.hasChild(user.uid)
                }
                else _mRequestStateLV.value = false
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.d(TAG, "mActiveReqListener_onCancelled: data cancelled}")
        }
    }

    init {
        val dbRef = database.reference
        mActiveReqRef = dbRef.child(DB_ACTIVE_REQUESTS)
        mActiveReqRef.addValueEventListener(mActiveReqListener)
    }

    fun sendTaxiRequest(latLng: LatLng){
        auth.currentUser?.let {user ->
            val dbRef = database.reference
            val activeReqRef = dbRef.child(DB_ACTIVE_REQUESTS).child(user.uid)

            activeReqRef.child(DB_LOCATION).setValue(latLng)
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
    }

    companion object{
        const val TAG = "my#RiderViewModel"
    }

}