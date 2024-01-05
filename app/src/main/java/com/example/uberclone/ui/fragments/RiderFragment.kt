package com.example.uberclone.ui.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.uberclone.R
import com.example.uberclone.vm.SharedViewModel


class RiderFragment: Fragment(R.layout.fragment_rider) {
    private val sharedViewModel by activityViewModels<SharedViewModel>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<Button>(R.id.btn_rider_logout).setOnClickListener {
            sharedViewModel.logoutUser()
        }
    }
}