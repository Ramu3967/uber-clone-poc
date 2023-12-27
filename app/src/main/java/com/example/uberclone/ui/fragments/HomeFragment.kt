package com.example.uberclone.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.uberclone.databinding.FragmentHomeBinding
import com.example.uberclone.utils.UberConstants.RIDER
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private val binding
        get() = _binding!!

    private var currentUserType = RIDER
    private var isReg = true

    private val auth by lazy { Firebase.auth }
    private val database by lazy { Firebase.database }

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
    }

    private fun validUI() = !binding.edtName.text.isNullOrEmpty() && !binding.edtPassword.text.isNullOrEmpty()

    private fun setupUI() {
        binding.btnSubmit.setOnClickListener {
            if(validUI()){
                val email = binding.edtName.text.toString()
                val pwd = binding.edtPassword.text.toString()
                if(isReg) registerUser(email, pwd, currentUserType)
                else loginUser(email, pwd)
            }
            else Toast.makeText(requireContext(), "Ensure all the details are filled", Toast.LENGTH_SHORT).show()
        }

        binding.rgUser.setOnCheckedChangeListener { _, checkedId ->
            val selectedRadioButton = requireActivity().findViewById<RadioButton>(checkedId)
            currentUserType = selectedRadioButton.text.toString()
        }

        binding.tvSubmitUtil.setOnClickListener{
            if(!isReg){
                binding.btnSubmit.text = "Register"
                binding.tvSubmitUtil.text = "Existing User? Login"
                binding.rgUser.visibility=View.VISIBLE
            }else{
                binding.btnSubmit.text = "Login"
                binding.tvSubmitUtil.text = "New User? Register"
                // no need of the radio group when logging in -> avoiding the case where the same user could be both the rider and the driver
                binding.rgUser.visibility=View.INVISIBLE

            }
            isReg=!isReg
        }
    }

    private fun loginUser(email: String, pwd: String) {
        auth.signInWithEmailAndPassword(email,pwd).addOnSuccessListener {
            // redirect to approp frag
            Log.d(TAG, "loginUser: login successful")

        }.addOnFailureListener {
            Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun registerUser(email: String, pwd: String,userType: String) {
        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener {result ->
                // save additional user data to a real time db
                saveUserDetails(result.user?.uid ?: "1", email, userType)
                Log.d(TAG, "setupUI: redirect to rider fragment")
            }.addOnFailureListener {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserDetails(uid: String, email: String, userType: String) {
        val dbRef = database.reference
        val userRef = dbRef.child("users").child(uid)
        userRef.child("email").setValue(email)
        userRef.child("userType").setValue(userType)
        userRef.addValueEventListener(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "onDataChange: value changed listener called")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(TAG, "onDataChange: value changed listener cancelled")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object{
        const val TAG = "my#homeFrag"
    }

}