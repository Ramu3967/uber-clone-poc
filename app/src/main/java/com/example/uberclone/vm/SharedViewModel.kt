package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.utils.HOMESCREENDIRECTIONS
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_DRIVER
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_HOME
import com.example.uberclone.utils.HOMESCREENDIRECTIONS.DIR_RIDER
import com.example.uberclone.utils.UberConstants.EMAIL
import com.example.uberclone.utils.UberConstants.RIDER
import com.example.uberclone.utils.UberConstants.USERS
import com.example.uberclone.utils.UberConstants.USER_TYPE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseUser
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
    var mToastLV = MutableLiveData("")
    var mHomeRedirectLV = MutableLiveData(DIR_HOME)
    var mLogoutLV = MutableLiveData("")

    // gets triggered by sign-out method of the auth
    private var myAuthStateListener = AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser ?:
        // sign out
        kotlin.run {
            // avoiding the logic when you open the app for the first time
            if(mHomeRedirectLV.value != DIR_HOME) {
                postSignOutOperations()
            }
        }
    }

    init {
        auth.addAuthStateListener(myAuthStateListener)
    }

    private fun postSignOutOperations() {
        mToastLV.value = "Successfully logged out"
        setRedirectDirection(DIR_HOME)
        mLogoutLV.value = DIR_HOME.toString()
        /* disconnect from firebase services
        *  remove listeners to avoid memory leaks */
        disconnectFirebase()
        Log.d(TAG, "postSignOutOperations: Log out successful")
    }

    fun loginUser(email: String, pwd: String) {
        auth.signInWithEmailAndPassword(email,pwd).addOnSuccessListener {result ->
            result.user?.let { loggedInUser ->
                checkUserAndRedirectIfNeeded(loggedInUser)
            }
            Log.d(TAG, "loginUser: login successful")
        }.addOnFailureListener {
            mToastLV.value = it.message
        }
    }

    private fun checkUserAndRedirectIfNeeded(loggedInUser: FirebaseUser) {
        val usersRef = database.reference.child(USERS)
        val loggedInUserRef = usersRef.child(loggedInUser.uid)
        loggedInUserRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    //user data is present in the db
                    val userType = snapshot.child(USER_TYPE).getValue(String::class.java)
                    setRedirectDirection(if (userType == RIDER) DIR_RIDER else DIR_DRIVER)
                } else {
                    mToastLV.value = "User data not found"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
    }

    fun registerUser(email: String, pwd: String,userType: String) {
        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener {result ->
                // save additional user data to a real time db
                saveUserDetails(result.user?.uid ?: "1", email, userType)
                Log.d(TAG, "setupUI: redirect to rider fragment")
            }.addOnFailureListener {
                mToastLV.value = it.message
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
                Log.e(TAG, "saveUserDetails: user data save unsuccessful")
            }
        })
    }

    private fun setRedirectDirection(direction: HOMESCREENDIRECTIONS) { mHomeRedirectLV.value = direction }

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

    fun checkAndRedirectIfNeeded() {
        auth.currentUser?.let {
            checkUserAndRedirectIfNeeded(loggedInUser = it)
        } ?: Log.d(TAG, "checkAndRedirectIfNeeded: no logged in user has been found")
    }

    companion object{
        const val TAG = "my#SharedViewModel"
    }
}
