package com.example.uberclone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.uberclone.databinding.FragmentHomeBinding
import com.example.uberclone.utils.UberConstants.RIDER
import com.example.uberclone.vm.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private val binding
        get() = _binding!!

    private var currentUserType = RIDER
    private var isRegister = true
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewUpdates()
    }

    private fun observeViewUpdates() {
        sharedViewModel.toastLV.observe(viewLifecycleOwner){
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun validUI() = !binding.edtName.text.isNullOrEmpty() && !binding.edtPassword.text.isNullOrEmpty()

    private fun setupUI() {
        binding.btnSubmit.setOnClickListener {
            if(validUI()){
                val email = binding.edtName.text.toString()
                val pwd = binding.edtPassword.text.toString()
                if(isRegister) sharedViewModel.registerUser(email, pwd, currentUserType)
                else sharedViewModel.loginUser(email, pwd)
            }
            else Toast.makeText(requireContext(), "Ensure all the details are filled", Toast.LENGTH_SHORT).show()
        }

        binding.rgUser.setOnCheckedChangeListener { _, checkedId ->
            val selectedRadioButton = requireActivity().findViewById<RadioButton>(checkedId)
            currentUserType = selectedRadioButton.text.toString()
        }

        binding.tvSubmitUtil.setOnClickListener{
            if(!isRegister){
                binding.btnSubmit.text = "Register"
                binding.tvSubmitUtil.text = "Existing User? Login"
                binding.rgUser.visibility=View.VISIBLE
            }else{
                binding.btnSubmit.text = "Login"
                binding.tvSubmitUtil.text = "New User? Register"
                // no need of the radio group when logging in -> avoiding the case where the same user could be both the rider and the driver
                binding.rgUser.visibility=View.INVISIBLE

            }
            isRegister=!isRegister
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object{
        const val TAG = "my#homeFrag"
    }

}