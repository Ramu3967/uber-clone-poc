package com.example.uberclone.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.uberclone.R
import com.example.uberclone.databinding.FragmentRiderBinding
import com.example.uberclone.utils.RideStatus
import com.example.uberclone.utils.TaxiConstants
import com.example.uberclone.vm.RiderViewModel
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
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject


@AndroidEntryPoint
class RiderFragment: Fragment(R.layout.fragment_rider) {
    private var _binding: FragmentRiderBinding? = null
    private val binding get() = _binding!!

    private val mSharedViewModel by activityViewModels<SharedViewModel>()
    private val mRiderViewModel by viewModels<RiderViewModel>()

    private var map: GoogleMap? = null
    private var mLastLatLng: LatLng? = null
    private var isRequestActive = false
    private var routeJob: Job? = null
    private var routeJobDestination: Job? = null
    private var mCurrentState = RideStatus.PENDING

    // static final destination location
    private var mDestination = LatLng(43.79731053924396, -79.33019465971456)

    @Inject
    lateinit var locationPermissions: Array<String>
    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val requestLocationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
        if(permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true){
            // proceed
            requestLocationUpdates()
            print("hello world")
        }else {
            Toast.makeText(requireContext(), "Location permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            Log.d(TAG, "onLocationResult: ${result.locations.last().latitude}${result.locations.last().longitude}")
            result.lastLocation?.let { location ->
                val lastKnownLatLng = LatLng(location.latitude, location.longitude)
                mLastLatLng = lastKnownLatLng
                mRiderViewModel.listenForDbChanges()
                mRiderViewModel.setCurrentRiderLocation(lastKnownLatLng)
                map?.updateCurrentLocationMarker(lastKnownLatLng)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRiderBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(savedInstanceState)
        checkAndRequestLocationPermissions()
        observeLiveData()
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        binding.btnRiderLogout.setOnClickListener { mSharedViewModel.logoutUser() }
        binding.mapView.getMapAsync {
            map = it
        }
        binding.mapView.onCreate(savedInstanceState)
        binding.btnCallTaxi.setOnClickListener {
            mLastLatLng?.let {
                if (!isRequestActive) {
                    mRiderViewModel.sendTaxiRequest(it)
                    // TODO: spin a loader and disable this button. Same goes for the below function.
                } else {
                    mRiderViewModel.cancelTaxiRequest()
                }
                isRequestActive = !isRequestActive
            } ?: Toast.makeText(requireContext(), "location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeLiveData() {
        mRiderViewModel.mRequestStateLV.observe(viewLifecycleOwner){
            if(it == null) return@observe
            isRequestActive = it
            if(it == false) binding.btnCallTaxi.text = "Call Taxi"
            else  binding.btnCallTaxi.text = "Cancel Taxi"
            // TODO: resolve the loader here and enable the button
        }

        mRiderViewModel.mCurrentStateLV.observe(viewLifecycleOwner){mCurrentState = it}

        // will be converted to a view-state
        mRiderViewModel.mDriverUpdatesLV.observe(viewLifecycleOwner){ driverUpdate ->
            val (driverLocation, notificationMsg,driverReached) = driverUpdate
            when(mCurrentState){
                RideStatus.PENDING -> {
                    // you don't get any updates when in this state
                }
                RideStatus.EN_ROUTE -> {
                    driverLocation?.let {  map?.updateDriverLocation(it) }
                    notificationMsg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
                    // reached rider's location
                    if(driverReached) {
                        Toast.makeText(requireContext(), "Your driver has arrived", Toast.LENGTH_SHORT).show()
                        mRiderViewModel.stopDriverUpdates()
                    }
                }
                RideStatus.EN_ROUTE_DEST -> {
                    driverLocation?.let { map?.run { updateLocationEnRouteDestination(this, it) } }
                }
                RideStatus.CANCELLED_ONGOING_RIDE -> {}
                RideStatus.FINISHED -> {}
            }
        }
    }

    private fun GoogleMap?.updateDriverLocation(driverLoc: LatLng) = this?.run {
        mLastLatLng?.let { riderLoc ->
            val polylineOptions = PolylineOptions()
                .width(5f)
                .color(Color.BLACK)

            routeJob?.cancel(CancellationException("new route incoming"))
            routeJob = lifecycleScope.launch(Dispatchers.IO) {
                val routePoints = mRiderViewModel.calculateRoute(driverLoc, riderLoc)
                Log.d("RoutePoints", routePoints.toString())

                polylineOptions.addAll(routePoints)

                withContext(Dispatchers.Main) {
                    clear()
                    // adding a marker for the rider
                    val riderMarkerOptions = MarkerOptions()
                        .position(riderLoc)
                        .title("Current Location")
                    addMarker(riderMarkerOptions)

                    // adding a marker for the driver
                    val driverMarkerOptions = MarkerOptions()
                        .position(driverLoc)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        .title("Driver's Location")
                    addMarker(driverMarkerOptions)

                    // Adding the polyline
                    addPolyline(polylineOptions)

                    val bounds = LatLngBounds.Builder()
                        .include(riderLoc)
                        .include(driverLoc)
                        .build()

                    animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, 150),
                        TaxiConstants.MAP_ANIMATION_DURATION,
                        null
                    )
                }
            }
        }
    }

    // club this operation with the above function
    private fun updateLocationEnRouteDestination(map:GoogleMap, driverLoc: LatLng) {
        routeJobDestination?.cancel(CancellationException("new route incoming"))
        routeJobDestination = lifecycleScope.launch(Dispatchers.IO) {
            val polylineOptions = PolylineOptions()
                .width(5f)
                .color(Color.BLACK)
            val riderLoc = mDestination
            val routePoints = mRiderViewModel.calculateRoute(driverLoc, riderLoc)
            Log.d("RoutePoints", routePoints.toString())
            polylineOptions.addAll(routePoints)
            withContext(Dispatchers.Main) {
                map.clear()
                // adding a marker for the rider
                val riderMarkerOptions = MarkerOptions()
                    .position(riderLoc)
                    .title("Destination")
                map.addMarker(riderMarkerOptions)

                // adding a marker for the driver
                val driverMarkerOptions = MarkerOptions()
                    .position(driverLoc)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    .title("Your Location")
                map.addMarker(driverMarkerOptions)

                // Adding the polyline
                map.addPolyline(polylineOptions)

                val bounds = LatLngBounds.Builder()
                    .include(riderLoc)
                    .include(driverLoc)
                    .build()

                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, 150),
                    TaxiConstants.MAP_ANIMATION_DURATION,
                    null
                )
            }
        }
    }

    private fun GoogleMap?.updateCurrentLocationMarker(latLng: LatLng) {
        // if there are any driver updates, ignore this logic and use the above function to update the marker.
        mRiderViewModel.mDriverUpdatesLV.value?.location?.let { return }
        this?.apply {
            // clearing the map
            clear()
            // adding a marker for the current location
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("Current Location")
            addMarker(markerOptions)
            // move the camera to the current location
            moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mLastLatLng = null
        requestLocationUpdates(false)
    }

    // managing the lifecycle of the map-view
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    private fun checkAndRequestLocationPermissions(){
        val pendingPermissions = locationPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if(pendingPermissions.isNotEmpty()) requestLocationPermissionsLauncher.launch(pendingPermissions)
        else{
            // proceed
            requestLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(boolean: Boolean = true) {
        if(boolean) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,TaxiConstants.LOCATION_INTERVAL_RIDER)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(TaxiConstants.LOCATION_FASTEST_INTERVAL_RIDER)
                .setMaxUpdateDelayMillis(TaxiConstants.LOCATION_MAX_WAIT_TIME_RIDER)
                .build()
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    companion object{
        const val TAG = "my#RiderFragment"
    }

}