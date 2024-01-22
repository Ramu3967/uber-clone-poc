package com.example.uberclone.vm

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.uberclone.network.IOSRMApiService
import com.example.uberclone.utils.DriverUpdates
import com.example.uberclone.utils.RideStatus
import com.example.uberclone.utils.TaxiConstants.DB_RIDER_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_DETAILS
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_ID
import com.example.uberclone.utils.TaxiConstants.DB_DRIVER_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_EMPTY_FIELD
import com.example.uberclone.utils.TaxiConstants.DB_END_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_FINISHED_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_START_LOCATION
import com.example.uberclone.utils.TaxiConstants.DB_ONGOING_REQUESTS
import com.example.uberclone.utils.TaxiConstants.DB_REQUESTED_AT
import com.example.uberclone.utils.TaxiConstants.DB_RIDE_STATUS
import com.example.uberclone.utils.TaxiConstants.DELIMITER
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MAX
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MIN
import com.example.uberclone.utils.TaxiConstants.DIST_NEAR_BY
import com.example.uberclone.utils.TaxiConstants.calculateDistance
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
    val mRequestStateLV get() = _mRequestStateLV

    var mDriverUpdatesLV = MutableLiveData(DriverUpdates())

    var mCurrentStateLV = MutableLiveData(RideStatus.PENDING)

    private var driverDbLocationRef: DatabaseReference? = null
    private lateinit var mRiderReqRef: DatabaseReference
    private lateinit var mOngoingReqRef: DatabaseReference
    private var mMyRequestDetails: DataSnapshot? = null

    private var mStartRiderLocation: LatLng? = null

    private var isRiderFirstNotification = false
    private var driverNearbyMessageSent = false

    private var isListeningForDb = false

    private lateinit var mRiderDestinationLocation: LatLng

    @Inject
    lateinit var api: IOSRMApiService

    private val mRiderRequestListener = object : ValueEventListener{
        override fun onDataChange(riderReqSnapshot: DataSnapshot) {
            auth.currentUser?.let {user ->
                val isReqExists = riderReqSnapshot.hasChild(user.uid)
                _mRequestStateLV.value = isReqExists // updates the button
                if(isReqExists){
                    val myRequest = riderReqSnapshot.child(user.uid)
                    mMyRequestDetails = myRequest
                    val status = myRequest.child(DB_RIDE_STATUS).getValue(RideStatus::class.java) ?: RideStatus.PENDING
                    mCurrentStateLV.value = status
                    val driverId = myRequest.child(DB_DRIVER_ID).getValue(String::class.java) ?: DB_EMPTY_FIELD
                    when(status) {
                        RideStatus.PENDING -> { }
                        RideStatus.EN_ROUTE -> {
                            // driver accepted the request, set up the listener on driver updates

                            if(driverId != DB_EMPTY_FIELD){
                                setDriverDbLocationListener(driverId)
                            }else{
                                Log.e(TAG, "onDataChange: Enroute but driver Id was not found")
                            }
                        }
                        RideStatus.EN_ROUTE_DEST -> {
                            Log.d(TAG, "onDataChange: driver picked up the rider and is moving to rider's destination")
                            if(driverId != DB_EMPTY_FIELD){
                                // set up the listener for location updates
                                setDriverDbLocationListener(driverId)
                                // move your location with the driver and draw a polyline to the destination
                            }else{
                                Log.e(TAG, "onDataChange: Enroute to destination but  driver Id was not found")
                            }
                        }
                        RideStatus.FINISHED -> {
                            mDriverUpdatesLV.value= DriverUpdates(driverId = driverId, msg = "Ride Ended")
                        }
                        RideStatus.CANCELLED_ONGOING_RIDE -> {}
                    }
                }else{
                    // moved to finished requests
                }
            }
        }

        override fun onCancelled(error: DatabaseError) {

        }

    }

    private fun setDriverDbLocationListener(driverId: String) {
        driverDbLocationRef = mOngoingReqRef.child("$driverId/$DB_DRIVER_DETAILS/$DB_DRIVER_LOCATION")
        driverNearbyMessageSent = false
        driverDbLocationRef?.addValueEventListener(mDiverLocationRefListener)
        // updates in this field by the driver shouldn't trigger the parent = ongoing requests listener.
        mOngoingReqRef.removeEventListener(mDiverLocationRefListener)
    }

    private val mDiverLocationRefListener = object: ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            val (lat,lon) = snapshot.getValue(String::class.java)!!.split(DELIMITER).map{ st -> st.toDouble() }
            val driverLocation = LatLng(lat,lon)
            if(mCurrentStateLV.value == RideStatus.EN_ROUTE){
                if(isRiderFirstNotification) {
                    mDriverUpdatesLV.value = DriverUpdates(location = driverLocation, msg = "The driver is on the way")
                    isRiderFirstNotification = false
                }
                else {
                    // todo: calculate distances and update the messages accordingly here - ex if the dist is <3KM notify the rider about the driver
                    val distanceRiderDriver = calculateDistance(mStartRiderLocation!!, driverLocation)
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
            else if(mCurrentStateLV.value == RideStatus.EN_ROUTE_DEST){
                val distanceDriverDestination = calculateDistance(driverLocation, mRiderDestinationLocation)
                mDriverUpdatesLV.value = when (distanceDriverDestination) {
                    in DIST_ARRIVAL_MIN..DIST_ARRIVAL_MAX -> {
                        // stop the updates from the driver
                        stopDriverUpdates()
                        DriverUpdates(location = driverLocation, msg = null, driverReached = true)
                    }
                    else -> DriverUpdates(location = driverLocation)
                }
            }

        }

        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "onCancelled: failed to add eventListener ${error.message}", )
            mDriverUpdatesLV.value = DriverUpdates()
        }
    }

    fun setCurrentRiderLocation(location: LatLng){
        mStartRiderLocation = location
    }

    suspend fun calculateRoute(source: LatLng, destination: LatLng) : List<LatLng>{
        // osrm accepts lon first then the lat
        val coordinates = "${source.longitude},${source.latitude};${destination.longitude},${destination.latitude}"
        val routeLatLng = mutableListOf<LatLng>()
        // add coroutine exception handler
        api.getRoutes(coordinates).let { result ->
            if(result.code() == 200){
                val osrmResponse = result.body()
                osrmResponse?.let {
                    if(it.code == "Ok"){
                        // assuming only one route
                        it.routes.first().geometry.coordinates.forEach{latlng ->
                            val (lon,lat) = latlng
                            routeLatLng.add(LatLng(lat,lon))
                        }
                    }
                }
            }
        }
        return routeLatLng
    }

    fun listenForDbChanges(){
        if(!isListeningForDb) {
            isListeningForDb = true
            val dbRef = database.reference
            mRiderReqRef = dbRef.child(DB_RIDER_REQUESTS)
//            mActiveReqRef.addValueEventListener(mActiveReqListener)
            mRiderReqRef.addValueEventListener(mRiderRequestListener)

            mOngoingReqRef = dbRef.child(DB_ONGOING_REQUESTS) // what if there's no ongoing req, how'd you search for the rider id?
        }
    }

    fun sendTaxiRequest(startLocation: LatLng, endLocation: LatLng){
        auth.currentUser?.let {user ->
            val dbRef = database.reference
            val activeReqRef = dbRef.child(DB_RIDER_REQUESTS).child(user.uid)
            val sLoc = "${startLocation.latitude}${DELIMITER}${startLocation.longitude}"
            val dLoc = "${endLocation.latitude}${DELIMITER}${endLocation.longitude}"
            val rideData = mapOf(
                DB_START_LOCATION to sLoc,
                DB_END_LOCATION to dLoc,
                DB_REQUESTED_AT to System.currentTimeMillis(),
                DB_RIDE_STATUS to RideStatus.PENDING.name,
                DB_DRIVER_ID to DB_EMPTY_FIELD
            )
            activeReqRef.updateChildren(rideData)
        } ?: Log.d(TAG,"taxiRequest: unable to send req as there's no user available")
    }

    fun cancelTaxiRequest(){
        auth.currentUser?.let { user ->
            val dbRef = database.reference
            val activeReqRef = dbRef.child(DB_RIDER_REQUESTS).child(user.uid)
            activeReqRef.removeValue()
        }
        Log.d(TAG, "cancelTaxiRequest: taxi cancelled")
    }

    override fun onCleared() {
        super.onCleared()
        if(::mRiderReqRef.isInitialized) mRiderReqRef.removeEventListener(mRiderRequestListener)
        stopDriverUpdates()
        isListeningForDb = false
    }

    fun stopDriverUpdates() {
//        when(mCurrentStateLV.value){
//            RideStatus.EN_ROUTE -> {}
//        }
        isRiderFirstNotification = false
        driverDbLocationRef?.removeEventListener(mDiverLocationRefListener)
        driverDbLocationRef = null
    }

    fun setDestinationLocation(destination: LatLng) {
        mRiderDestinationLocation = destination
    }

    fun resetRiderState(driverId: String) {
        val importantData: Any? = null // to be sent to finished state such as timestamps of pickups and drop off or any other info
        removeFromOngoingRequests(driverId)
        // move riderRequest to finished request with R:D:T childID
        saveToFinishedRides(driverId)
        mCurrentStateLV.value = RideStatus.PENDING
        _mRequestStateLV.value = false
    }

    private fun removeFromOngoingRequests(driverId: String) {
        mOngoingReqRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(ongoingRides: DataSnapshot) {
                if (ongoingRides.hasChild(driverId)) {
                    // save imp data from this request like timestamps for future analysis
                    mOngoingReqRef.child(driverId).removeValue()
                }
                // no need to listen after removing this request
                mOngoingReqRef.removeEventListener(this)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    TAG,
                    "ResetState_OngoingReq_onCancelled: failed with message ${error.message}"
                )
                mOngoingReqRef.removeEventListener(this)
            }
        })
    }

    /**
     * handle finished and canceled rides
     */
    private fun saveToFinishedRides(driverId: String) {
        // currently handling only finished rides as the cancel flow isn't implemented yet
        auth.currentUser?.uid.let {riderId ->
            val finishedRidesRef = database.reference.child(DB_FINISHED_REQUESTS)
            val timeStamp = System.currentTimeMillis()
            val uniqueID = "$riderId$DELIMITER$driverId$DELIMITER$timeStamp"
            mMyRequestDetails?.run {
                // on condition that one request/rider
                val (endLat, endLon) = this.child(DB_END_LOCATION).getValue(String::class.java)!!.split(DELIMITER).map{ it.toDouble() }
                val (startLat, startLon) = this.child(DB_START_LOCATION).getValue(String::class.java)!!.split(DELIMITER).map{ it.toDouble() }
                val rideStatus = this.child(DB_RIDE_STATUS).getValue(String::class.java)!!
                val requestAt = this.child(DB_REQUESTED_AT).getValue(Long::class.java)!!

                val children = mapOf(
                    DB_END_LOCATION to "$endLat,$endLon",
                    DB_START_LOCATION to "$startLat,$startLon",
                    DB_RIDE_STATUS to rideStatus,
                    DB_REQUESTED_AT to requestAt
                )
                finishedRidesRef.child(uniqueID).updateChildren(children)

            } ?: Log.e(TAG, "failed to move to finished rides, reason - unable to fetch riderRequest details")
        }
    }

    companion object{
        const val TAG = "my#RiderViewModel"
    }

}