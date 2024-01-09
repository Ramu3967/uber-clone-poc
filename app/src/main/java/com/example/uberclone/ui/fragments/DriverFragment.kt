package com.example.uberclone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.uberclone.databinding.FragmentDriverBinding
import com.example.uberclone.utils.LocationUtils.updateCurrentLocationMarker
import com.example.uberclone.utils.TaxiConstants.DRIVER
import com.example.uberclone.utils.TaxiConstants.calculateDistance
import com.example.uberclone.utils.TaxiRequest
import com.example.uberclone.vm.DriverViewModel
import com.example.uberclone.vm.SharedViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
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
        mDriverViewModel.mUserRequestsLV.observe(viewLifecycleOwner){
            if(it.isNotEmpty()){
                mActiveRequests = it
                mLastLatLng?.let {
                    // TODO: change this adapter logic
                    binding.lvRequests.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mActiveRequests.map {taxiRequest ->
                        calculateDistance(source = it, destination = taxiRequest.location)
                    })
                }
            }else{
                mActiveRequests = it
                binding.lvRequests.adapter = ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1, mActiveRequests)
            }
        }
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        binding.mapViewDriver.apply {
            getMapAsync { map = it }
            onCreate(savedInstanceState)
        }
        binding.btnDriverLogout.setOnClickListener { mSharedViewModel.logoutUser() }
        binding.lvRequests.apply {
            adapter = ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1, mActiveRequests)
            onItemClickListener = AdapterView.OnItemClickListener { p0, p1, pos, p3 ->

            }
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