package com.example.uberclone.ui.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.uberclone.databinding.FragmentDriverBinding
import com.example.uberclone.ui.adapters.DriverRequestsAdapter
import com.example.uberclone.utils.RideStatus
import com.example.uberclone.utils.TaxiConstants
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MAX
import com.example.uberclone.utils.TaxiConstants.DIST_ARRIVAL_MIN
import com.example.uberclone.utils.TaxiConstants.LOCATION_FASTEST_INTERVAL
import com.example.uberclone.utils.TaxiConstants.LOCATION_INTERVAL
import com.example.uberclone.utils.TaxiConstants.LOCATION_MAX_WAIT_TIME
import com.example.uberclone.utils.TaxiConstants.MAP_ANIMATION_DURATION
import com.example.uberclone.utils.TaxiRequest
import com.example.uberclone.vm.DriverViewModel
import com.example.uberclone.vm.SharedViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


// TODO: currently location permissions are handled only in the rider fragment, need to be shifted to home fragment
@AndroidEntryPoint
class DriverFragment: Fragment() {
    private var _binding: FragmentDriverBinding? = null
    private val binding: FragmentDriverBinding
        get() = _binding!!

    private var map: GoogleMap? = null
    private var mLastLatLng: LatLng? = null
    private var mLastSelectedRequest: TaxiRequest? = null

    private var mCurrRideStatus = RideStatus.PENDING

    private val mRequestAdapter by lazy{
        DriverRequestsAdapter(driverLocation = mLastLatLng, taxiRequests = mActiveRequests){ selectedTaxiRequest ->
            // take care of end Location (needed when destination ride starts)
            addDestinationAndAdjustMap(mLastLatLng!!, selectedTaxiRequest.startLocation, "Rider's Location")
            mLastSelectedRequest = selectedTaxiRequest
        }
    }

    private var mRiderMarker: Marker? = null
    private var mDriverMarker: Marker? = null

    private var mActiveRequests: List<TaxiRequest> = emptyList()

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val mSharedViewModel by activityViewModels<SharedViewModel>()
    private val mDriverViewModel by viewModels<DriverViewModel>()

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            Log.d(TAG, "onLocationResult: ${result.locations.last().latitude}${result.locations.last().longitude}")
            result.lastLocation?.let { location ->
                val lastKnownDriverLatLng = LatLng(location.latitude, location.longitude)
                mLastLatLng = lastKnownDriverLatLng

                mLastSelectedRequest?.let {riderRequest ->
                    when(mCurrRideStatus){
                        RideStatus.PENDING -> {}
                        RideStatus.EN_ROUTE -> {
                            // riding towards the rider
                            val distanceRemaining = TaxiConstants.calculateDistance(lastKnownDriverLatLng, riderRequest.startLocation)
                            if (distanceRemaining in DIST_ARRIVAL_MIN..DIST_ARRIVAL_MAX){
                                // driver reached the rider
                                requestLocationUpdates(false) // no more location updates to rider and firebase
                                showDialogToStartDestinationRide(src=lastKnownDriverLatLng, taxiRequest =riderRequest)
                            }
                        }
                        RideStatus.EN_ROUTE_DEST -> {
                            // riding towards the rider's destination
                            val distanceRemaining = TaxiConstants.calculateDistance(lastKnownDriverLatLng, riderRequest.endLocation)
                            if (distanceRemaining in DIST_ARRIVAL_MIN..DIST_ARRIVAL_MAX){
                                // driver reached the destination
                                requestLocationUpdates(false) // no more location updates to rider and firebase
                                showRideCompletedDialog()
                            }
                        }
                        RideStatus.CANCELLED_ONGOING_RIDE -> {}
                        RideStatus.FINISHED -> {}
                    }
                }

                mDriverViewModel.listenForDbChanges()
                map?.updateCurrentLocationMarker(lastKnownDriverLatLng)
                // update driver location on firebase
                mDriverViewModel.updateDriverLocationInOngoingReqToFirebase(driverLocation = mLastLatLng!!)
            }
        }
    }

    private fun showDialogToStartDestinationRide(src: LatLng, taxiRequest: TaxiRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Start the ride to destination")
            .setCancelable(false)
            .setPositiveButton("Start"){ dialog, i ->
                requestLocationUpdates(true)
                // todo update the rider's marker on driver map
                addDestinationAndAdjustMap(srcLocation = src, desLocation = taxiRequest.endLocation, destinationMarkerText = "Drop-off Location")
                mDriverViewModel.startNavigationToDestination(taxiRequest)
                mCurrRideStatus = RideStatus.EN_ROUTE_DEST
            }
            .setNegativeButton("Cancel"){ dialog, i ->
                Log.d(TAG, "showDialogToStartDestinationRide: Cancel Clicked")
            }.show()
    }

    private fun showRideCompletedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Drop off the rider")
            .setCancelable(false)
            .setPositiveButton("Start"){ dialog, i ->
                requestLocationUpdates(false)
                // todo process the payment at the end of the ride
                Log.d(TAG, "showRideCompletedDialog: ride Completed")
                // todo move the riderRequest to FinishedRequests
            }
            .setNegativeButton("Cancel"){ dialog, i ->
                Log.d(TAG, "showRideCompletedDialog: ride canceled")
            }.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(savedInstanceState)
        requestLocationUpdates()
        observeLiveData()
    }

    private fun observeLiveData() {
        mDriverViewModel.mUserRequestsLV.observe(viewLifecycleOwner){ taxiRequests ->
            mActiveRequests = taxiRequests
            mLastLatLng?.let {
                mRequestAdapter.submitList(mActiveRequests, mLastLatLng)
            }
            mActiveRequests.isNotEmpty().let {
                binding.btnAcceptRequest.isVisible = it
                if(!it) mLastSelectedRequest = null
            }
        }

        mDriverViewModel.mNavigationLV.observe(viewLifecycleOwner){
            it?.let {destination ->
//                startNavigation(src = mLastLatLng!!, des = destination)
            }
        }
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        binding.mapViewDriver.apply {
            getMapAsync { map = it }
            onCreate(savedInstanceState)
        }
        binding.btnDriverLogout.setOnClickListener { mSharedViewModel.logoutUser() }
        binding.rvRequests.apply {
            // TODO: DiffUtil approach could be used
            adapter = mRequestAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL,false)
        }
        binding.btnAcceptRequest.setOnClickListener { acceptTaxiRequest() }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(boolean: Boolean = true) {
        if(boolean) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_MAX_WAIT_TIME)
                .build()
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun addDestinationAndAdjustMap(srcLocation: LatLng, desLocation: LatLng, destinationMarkerText: String){
        mRiderMarker?.remove()
        val riderMarkerOptions = MarkerOptions()
            .position(desLocation)
            .title(destinationMarkerText)
        mRiderMarker = map?.addMarker(riderMarkerOptions)

        mDriverMarker?.remove()
        val driverMarkerOptions = MarkerOptions()
            .position(srcLocation)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            .title("Current Location")
        mDriverMarker = map?.addMarker(driverMarkerOptions)

        // constructing a bound to fit two points on the map
        val bounds = LatLngBounds.Builder()
            .include(srcLocation)
            .include(desLocation)
            .build()
        // moving the camera
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,150),
            MAP_ANIMATION_DURATION, null)
    }

    private fun GoogleMap?.updateCurrentLocationMarker(driverLocation: LatLng) {
        mRiderMarker?.let {
            // don't make api call when the driver's position isn't changed much from the previous known location
            addDestinationAndAdjustMap(srcLocation = driverLocation, desLocation = it.position, "Rider's Location")
        } ?: kotlin.run {
            // clearing the map
            this?.clear()
            // adding a marker for the current location
            val markerOptions = MarkerOptions()
                .position(driverLocation)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .title("Current Location")
            mDriverMarker = this?.addMarker(markerOptions)
            // move the camera to the current location
            this?.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLocation, 15f))
        }
    }

    private fun acceptTaxiRequest(){
        mLastSelectedRequest?.let {
            mDriverViewModel.acceptTaxiRequest(it, driverLocation = mLastLatLng!!)
            mCurrRideStatus = RideStatus.EN_ROUTE
        } ?: Toast.makeText(requireContext(),"unable to accept this request", Toast.LENGTH_SHORT).show()
    }

    private fun startNavigation(src:LatLng, des: LatLng) {
        // Create a URI for the source and destination locations
        val uri = "http://maps.google.com/maps?saddr=${src.latitude},${src.longitude}&daddr=${des.latitude},${des.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

        // Specify the package to ensure that the intent is handled by Google Maps
        intent.setPackage("com.google.android.apps.maps")

        // Check if there's an app available to handle the intent
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            // If Google Maps is not installed, you can handle this case or prompt the user to install it
            // Alternatively, you can use a different navigation app
            Toast.makeText(requireContext(), "Google Maps app not installed", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mLastLatLng = null
        mLastSelectedRequest = null
        mDriverMarker = null
        mRiderMarker = null
        requestLocationUpdates(false)
    }

    // managing the lifecycle of the map-view
    override fun onResume() {
        super.onResume()
        binding.mapViewDriver.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapViewDriver.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapViewDriver.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewDriver.onLowMemory()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewDriver.onPause()
    }

    companion object{
        const val TAG = "my#DriverFragment"
    }
}