package com.example.healthfitness.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.healthfitness.R


class NewExerciseConfirmation : Fragment(R.layout.fragment_new_exercise_confirmation) {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_new_exercise_confirmation, container, false)
    }

}