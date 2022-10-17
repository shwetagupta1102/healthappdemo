package com.example.healthfitness.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.healthfitness.R
import com.example.healthfitness.servicemanager.HealthFitnessManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class StartupFragment : Fragment(R.layout.fragment_start_up) {

    @Inject
    lateinit var healthServicesManager: HealthFitnessManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val destination = if(healthServicesManager.hasExerciseCapability()) {
                    R.id.prepareFragment
                }
                else {
                    R.id.notAvailableFragment
                }
                findNavController().navigate(destination)
            }
        }
    }
}

/**
 * Fragment shown when exercise capability is not available.
 */
@AndroidEntryPoint
class NotAvailableFragment : Fragment(R.layout.fragment_not_available)
