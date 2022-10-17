package com.example.healthfitness.activity

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.healthfitness.R
import com.example.healthfitness.application.TAG
import com.example.healthfitness.databinding.ActivityPassiveBinding
import com.example.healthfitness.viewmodel.PassiveViewModel
import com.example.healthfitness.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class PassiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPassiveBinding
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val viewModel: PassiveViewModel by viewModels()

    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPassiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.i(TAG, "Activity recognition permission granted")
                    viewModel.togglePassiveGoals(true)
                } else {
                    Log.i(TAG, "Activity recognition permission not granted")
                    viewModel.togglePassiveGoals(false)
                }
            }

        binding.enablePassiveGoals.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Make sure we have the necessary permission first.
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    viewModel.togglePassiveGoals(true)
                }
            } else {
                viewModel.togglePassiveGoals(false)
            }
        }

        // Bind viewmodel state to the UI.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // As collect is a suspend function, if you want to collect
                // multiple flows in parallel, you need to do so in
                // different coroutines
                launch {
                    viewModel.uiState.collect {
                        updateViewVisibility(it)
                    }


                }
                launch {
                    viewModel.passiveGoalsEnabled.collect {
                        binding.enablePassiveGoals.isChecked = it
                    }
                }
                launch {
                    viewModel.dailyStepsGoalAchieved.collect {
                        Log.e("PassiveActivity","StepsGoalcollect $it")
                        updateDailyStepsGoal(it)
                    }
                }
                launch {

                    viewModel.latestFloorsGoalTime.collect {
                        Log.e("PassiveActivity","floorsGoalcollect $it")
                        updateFloorsGoal(it)
                    }
                }
            }
        }
    }

    private fun updateViewVisibility(uiState: UiState) {

        Log.e(TAG,"uiState ${UiState.CapabilitiesAvailable}")

        (uiState is UiState.Startup).let {
            Log.e(TAG,"UiState.Startup $it" )

            binding.progress.isVisible = it
        }
        // These views are visible when steps and floors capabilities are not available.
        (uiState is UiState.CapabilitiesNotAvailable).let {
            Log.e(TAG,"UiState.CapabilitiesNotAvailable $it" )

            binding.notAvailableIcon.isVisible = it
            binding.notAvailableText.isVisible = it
        }
        // These views are visible when the capability is available.
        (uiState is UiState.CapabilitiesAvailable).let {
            Log.e(TAG,"UiState.CapabilitiesAvailable $it" )
            binding.enablePassiveGoals.isVisible = it
            binding.dailyStepsIcon.isVisible = it
            binding.dailyStepsText.isVisible = it

            binding.floorsIcon.isVisible = it
            binding.floorsText.isVisible = it
            Log.e(TAG,"Floors $it" )
        }
    }

    private fun updateFloorsGoal(time: Instant) {
       Log.e("PassiveActivity","updateFloorsGoal $time")
        val iconTint = if (time.toEpochMilli() == 0L)
            Color.GRAY
        else
            resources.getColor(R.color.primary_orange, null)
        binding.floorsIcon.imageTintList = ColorStateList.valueOf(iconTint)
        binding.floorsText.text = if (time.toEpochMilli() == 0L)
            getString(R.string.waiting_for_goal)
        else
            getString(R.string.floor_goal, timeFormatter.format(time))
    }

    private fun updateDailyStepsGoal(isAchieved: Boolean) {
       Log.e("PassiveActivity","isAchieved $isAchieved")
        val iconTint = if (isAchieved)
            resources.getColor(R.color.primary_orange, null)
        else
            Color.GRAY
        binding.dailyStepsIcon.imageTintList = ColorStateList.valueOf(iconTint)
        binding.dailyStepsText.text = if (isAchieved) {
            getString(R.string.daily_steps_goal_achieved)
        } else {
            getString(R.string.daily_steps_goal_not_achieved)
        }
    }
}