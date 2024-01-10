package com.example.uberclone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.uberclone.databinding.FragmentDriverBinding
import com.example.uberclone.ui.adapters.RequestsAdapter
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
    private val mRequestAdapter by lazy{
        RequestsAdapter(driverLocation = mLastLatLng, taxiRequests = mActiveRequests){des ->
            addDestinationAndAdjustMap(mLastLatLng!!, des)
        }
    }

    private var desMarker: Marker? = null

    private var mActiveRequests: List<TaxiRequest> = emptyList()

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val mSharedViewModel by activityViewModels<SharedViewModel>()
    private val mDriverViewModel by viewModels<DriverViewModel>()

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            Log.d(RiderFragment.TAG, "onLocationResult: ${result.locations.last().latitude}")
            result.lastLocation?.let { location ->
                val lastKnownLatLng = LatLng(location.latitude, location.longitude)
                mLastLatLng = lastKnownLatLng
                mDriverViewModel.listenForDbChanges()
                map?.updateCurrentLocationMarker(lastKnownLatLng)
            }
        }
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
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(boolean: Boolean = true) {
        if(boolean) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(20000)
                .setMaxUpdateDelayMillis(30000)
                .build()
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun addDestinationAndAdjustMap(src: LatLng, des: LatLng){
        desMarker?.remove()
        val newMarkerOptions = MarkerOptions()
            .position(des)
            .title("Rider's Location")
        desMarker = map?.addMarker(newMarkerOptions)

        // constructing a bound to fit two points on the map
        val bounds = LatLngBounds.Builder()
            .include(src)
            .include(des)
            .build()
        // moving the camera
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,150),
            MAP_ANIMATION_DURATION, null)
    }

    private fun GoogleMap?.updateCurrentLocationMarker(driverLocation: LatLng) {
        desMarker?.let {
            addDestinationAndAdjustMap(src = driverLocation, des = it.position)
        } ?: kotlin.run {
            // clearing the map
            this?.clear()
            // adding a marker for the current location
            val markerOptions = MarkerOptions()
                .position(driverLocation)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .title("Current Location")
            this?.addMarker(markerOptions)
            // move the camera to the current location
            this?.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLocation, 15f))
        }

//        this?.apply {
//            // clearing the map
//            clear()
//            // adding a marker for the current location
//            val markerOptions = MarkerOptions()
//                .position(latLng)
//                .title("Current Location")
//            addMarker(markerOptions)
//            // move the camera to the current location
//            moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
//        }
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
}