package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_DRIVER
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_INITIAL
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_RIDER
import com.example.uberclone.utils.UberConstants.EMAIL
import com.example.uberclone.utils.UberConstants.RIDER
import com.example.uberclone.utils.UberConstants.USERS
import com.example.uberclone.utils.UberConstants.USER_TYPE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    // introduce an interface for auth and db
    private var auth: FirebaseAuth,
    private var database: FirebaseDatabase
): ViewModel() {

    // toast lv for the time being, will be replaced with a viewState object.
    var toastLV = MutableLiveData("")
    var redirectLV = MutableLiveData(DIR_INITIAL)

    // gets triggered by sign-out method of the auth
    private var myAuthStateListener = AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser ?:
        // sign out
        kotlin.run {
            // avoiding the logic when you open the app for the first time
            if(redirectLV.value != DIR_INITIAL) {
                postSignOutOperations()
            }
        }
    }

    init {
        auth.addAuthStateListener(myAuthStateListener)
    }

    private fun postSignOutOperations() {
        toastLV.value = "Successfully logged out"
        redirectLV.value = DIR_INITIAL
        /* disconnect from firebase services
        *  remove listeners to avoid memory leaks */
        disconnectFirebase()
        Log.d(TAG, "postSignOutOperations: Log out successful")
    }

    fun loginUser(email: String, pwd: String) {
        auth.signInWithEmailAndPassword(email,pwd).addOnSuccessListener {result ->
            // redirect to appropriate frag
            result.user?.let { loggedInUser ->
                val usersRef = database.reference.child(USERS)
                val loggedInUserRef = usersRef.child(loggedInUser.uid)
                loggedInUserRef.addValueEventListener(object : ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if(snapshot.exists()){
                            //user data is present in the db
                            val userType = snapshot.child(USER_TYPE).getValue(String::class.java)
                            redirectLV.value = if (userType == RIDER) DIR_RIDER else DIR_DRIVER
                        }else{
                            toastLV.value = "User data not found"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        TODO("Not yet implemented")
                    }
                })
            }
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
        val userRef = dbRef.child(USERS).child(uid)
        userRef.child(EMAIL).setValue(email)
        userRef.child(USER_TYPE).setValue(userType)
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

    fun logoutUser(){
        auth.signOut()
    }

    override fun onCleared() {
        super.onCleared()
        disconnectFirebase()
    }

    private fun disconnectFirebase() {
        auth.removeAuthStateListener(myAuthStateListener)
        database.goOffline()
    }

    companion object{
        const val TAG = "my#SharedViewModel"
    }
}
