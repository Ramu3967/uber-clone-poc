package com.example.uberclone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.uberclone.R
import com.example.uberclone.databinding.FragmentRiderBinding
import com.example.uberclone.vm.SharedViewModel
import com.google.android.gms.maps.GoogleMap


class RiderFragment: Fragment(R.layout.fragment_rider) {
    private var _binding: FragmentRiderBinding? = null
    private val sharedViewModel by activityViewModels<SharedViewModel>()
    private val binding get() = _binding!!

    private var map: GoogleMap? = null

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
        binding.btnRiderLogout.setOnClickListener { sharedViewModel.logoutUser() }
        binding.mapView.getMapAsync {
            map = it
        }
        binding.mapView.onCreate(savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

}