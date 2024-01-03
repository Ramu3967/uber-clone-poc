package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private var auth: FirebaseAuth,
    private var database: FirebaseDatabase
): ViewModel() {

    // toast lv for the time being, will be replaced with a viewState object.
    var toastLV = MutableLiveData("")

    fun loginUser(email: String, pwd: String) {
        auth.signInWithEmailAndPassword(email,pwd).addOnSuccessListener {
            // redirect to approp frag
            Log.d(TAG, "loginUser: login successful")

        }.addOnFailureListener {
            toastLV.value = it.message
        }
    }

    fun registerUser(email: String, pwd: String,userType: String) {
        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener {result ->
                // save additional user data to a real time db
                saveUserDetails(result.user?.uid ?: "1", email, userType)
                Log.d(TAG, "setupUI: redirect to rider fragment")
            }.addOnFailureListener {
                toastLV.value = it.message
            }
    }

    private fun saveUserDetails(uid: String, email: String, userType: String) {
        val dbRef = database.reference
        val userRef = dbRef.child("users").child(uid)
        userRef.child("email").setValue(email)
        userRef.child("userType").setValue(userType)
        // use a separate function for these listeners on the db references.
        userRef.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "onDataChange: value changed listener called")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(TAG, "onDataChange: value changed listener cancelled")
            }
        })
    }

    companion object{
        const val TAG = "my#SharedViewModel"
    }
}
