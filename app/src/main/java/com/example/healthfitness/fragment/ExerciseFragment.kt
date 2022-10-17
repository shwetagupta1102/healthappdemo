

package com.example.healthfitness.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.concurrent.futures.await
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.health.services.client.data.AggregateDataPoint
import androidx.health.services.client.data.CumulativeDataPoint
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.wear.ambient.AmbientModeSupport
import com.example.healthfitness.R
import com.example.healthfitness.application.TAG

import com.example.healthfitness.databinding.FragmentExerciseBinding
import com.example.healthfitness.service.ActiveDurationUpdate
import com.example.healthfitness.service.ExerciseService
import com.example.healthfitness.service.ExerciseServiceConnection
import com.example.healthfitness.servicemanager.HealthFitnessManager
import com.example.healthfitness.utils.formatCalories
import com.example.healthfitness.utils.formatDistanceKm
import com.example.healthfitness.utils.formatElapsedTime
import com.example.healthfitness.utils.formatSteps
import com.example.healthfitness.viewmodel.AmbientEvent
import com.example.healthfitness.viewmodel.HealthFitnessViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Fragment showing the exercise controls and current exercise metrics.
 */
@AndroidEntryPoint
class ExerciseFragment : Fragment(R.layout.fragment_exercise) {

    @Inject
    lateinit var healthServicesManager: HealthFitnessManager

    private val viewModel: HealthFitnessViewModel by activityViewModels()

    private var _binding: FragmentExerciseBinding? = null
    private val binding get() = _binding!!

    private var serviceConnection = ExerciseServiceConnection()

    private var cachedExerciseState = ExerciseState.USER_ENDED
    private var activeDurationUpdate = ActiveDurationUpdate()
    private var chronoTickJob: Job? = null
    private var uiBindingJob: Job? = null

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private lateinit var ambientModeHandler: AmbientModeHandler
    var count = 0L
    var prevCount = 0L
    var i = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startEndButton.setOnClickListener {
            // App could take a perceptible amount of time to transition between states; put button into
            // an intermediary "disabled" state to provide UI feedback.
            it.isEnabled = false
            startEndExercise()
        }
        binding.pauseResumeButton.setOnClickListener {
            // App could take a perceptible amount of time to transition between states; put button into
            // an intermediary "disabled" state to provide UI feedback.
            it.isEnabled = false
            pauseResumeExercise()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                val capabilities =
                    healthServicesManager.getExerciseCapabilities() ?: return@repeatOnLifecycle
                Log.e("ExerciseFragment","capabilities $capabilities")
                           val supportedTypes = capabilities.supportedDataTypes


                // Set enabled state for relevant text elements.
                Log.e("ExerciseFragment","HeartRateBMP ${DataType.HEART_RATE_BPM in supportedTypes}")
                Log.e("ExerciseFragment","Steps ${DataType.STEPS in supportedTypes}")
               // Log.e("ExerciseFragment","DailySteps ${DataType.DAILY_STEPS in supportedTypes}")

                binding.heartRateText.isEnabled = DataType.HEART_RATE_BPM in supportedTypes
                binding.caloriesText.isEnabled = DataType.TOTAL_CALORIES in supportedTypes
                binding.distanceText.isEnabled = DataType.DISTANCE in supportedTypes
                //binding.lapsText.isEnabled = true
                //binding.floorText.isEnabled = DataType.FLOORS in supportedTypes
                binding.stepsText.isEnabled = DataType.STEPS in supportedTypes


            }
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.keyPressFlow.collect {
                    healthServicesManager.markLap()
                }
            }
        }

        // Ambient Mode
        ambientModeHandler = AmbientModeHandler()
        ambientController = AmbientModeSupport.attach(requireActivity())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ambientEventFlow.collect {

                    Log.e("ExerciseFragment","onAmbientEvent")

                    ambientModeHandler.onAmbientEvent(it)
                }
            }
        }

        // Bind to our service. Views will only update once we are connected to it.
        ExerciseService.bindService(requireContext().applicationContext, serviceConnection)
        bindViewsToService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unbind from the service.
        ExerciseService.unbindService(requireContext().applicationContext, serviceConnection)
        _binding = null
    }

    private fun startEndExercise() {
        if (cachedExerciseState.isEnded) {
            tryStartExercise()
        } else {
            checkNotNull(serviceConnection.exerciseService) {
                "Failed to achieve ExerciseService instance"
            }.endExercise()
        }
    }

    private fun tryStartExercise() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (healthServicesManager.isTrackingExerciseInAnotherApp()) {
                // Show the user a confirmation screen.
                findNavController().navigate(R.id.to_newExerciseConfirmation)
            } else if (!healthServicesManager.isExerciseInProgress()) {
                checkNotNull(serviceConnection.exerciseService) {
                    "Failed to achieve ExerciseService instance"
                }.startExercise()
            }
        }
    }

    private fun pauseResumeExercise() {
        val service = checkNotNull(serviceConnection.exerciseService) {
            "Failed to achieve ExerciseService instance"
        }
        if (cachedExerciseState.isPaused) {
            service.resumeExercise()
        } else {
            service.pauseExercise()
        }
    }

    private fun bindViewsToService() {
        if (uiBindingJob != null) return

        uiBindingJob = viewLifecycleOwner.lifecycleScope.launch {
            serviceConnection.repeatWhenConnected { service ->
                // Use separate launch blocks because each .collect executes indefinitely.
                launch {
                    service.exerciseState.collect {
                        updateExerciseStatus(it)
                    }
                }
                launch {
                    service.exerciseMetrics.collect {
                        updateMetrics(it)
                    }
                }
                launch {
                    service.aggregateMetrics.collect {
                        updateAggregateMetrics(it)
                    }
                }
                launch {
                    service.exerciseLaps.collect {
                        updateLaps(it)
                    }
                }
                launch {
                    service.exerciseDurationUpdate.collect {
                        // We don't update the chronometer here since these updates come at irregular
                        // intervals. Instead we store the duration and update the chronometer with
                        // our own regularly-timed intervals.
                        activeDurationUpdate = it
                    }
                }
            }
        }
    }

    private fun unbindViewsFromService() {
        uiBindingJob?.cancel()
        uiBindingJob = null
    }

    private fun updateExerciseStatus(state: ExerciseState) {
        val previousStatus = cachedExerciseState
        Log.e("ExerciseFragment","previousStatus $previousStatus")
        Log.e("ExerciseFragment","state ${state.isEnded}")
        Log.e("ExerciseFragment","previousStatus.isEnded ${previousStatus.isEnded}")

        if (previousStatus.isEnded && !state.isEnded) {
            // We're starting a new exercise. Clear metrics from any prior exercise.
            Log.e("ExerciseFragment","previousStatus236 $previousStatus")
            resetDisplayedFields()

        }
        if (state == ExerciseState.ACTIVE && !ambientController.isAmbient) {

            startChronometer()

        } else {

            stopChronometer()

        }

        updateButtons(state)
        cachedExerciseState = state
    }

    private fun updateButtons(state: ExerciseState) {
        binding.startEndButton.setText(if (state.isEnded) R.string.start else R.string.end)
        binding.startEndButton.isEnabled = true
        binding.pauseResumeButton.setText(if (state.isPaused) R.string.resume else R.string.pause)
        binding.pauseResumeButton.isEnabled = !state.isEnded
    }

    private fun updateMetrics(data: Map<DataType, List<DataPoint>>) {
        data[DataType.HEART_RATE_BPM]?.let {
            Log.e("ExerciseFragment","HeartRate ${it.last().value.asDouble().roundToInt().toString()}")
            val heartRate = it.last().value.asDouble().roundToInt()
            Log.e("ExerciseFragment","heartRate ${heartRate}")
            if(heartRate > 182){
                Toast.makeText(requireContext(),"Warning! Heart rate exceeding",Toast.LENGTH_SHORT).show()
            }
            binding.heartRateText.text = it.last().value.asDouble().roundToInt().toString()
        }

        data[DataType.STEPS]?.let {
            Log.e("ExerciseFragment","DailySteps ${it.last().value}")
            count = prevCount + it.last().value.asLong()
            prevCount = count
            Log.e("ExerciseFragment","PrevCount ${prevCount}")
            Log.e("ExerciseFragment","Count ${count}")
            var newCount = count.toInt()
            if((newCount >= 100) && (newCount <= 150) ){

                Toast.makeText(requireContext(),"Congratulations! you receive 1 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 200) && (newCount <= 250) ){
              //  i++
                Toast.makeText(requireContext(),"Congratulations! you receive 2 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 300) && (newCount <= 350) ){
               // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 3 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 400) && (newCount <= 450) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 4 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 500) && (newCount <= 550) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 5 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 600) && (newCount <= 650) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 6 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 700) && (newCount <= 750) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 7 heart points",Toast.LENGTH_SHORT).show()
            }
            else if((newCount >= 800) && (newCount <= 850) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 8 heart points",Toast.LENGTH_SHORT).show()
            }   else if((newCount >= 900) && (newCount <= 950) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you receive 9 heart points",Toast.LENGTH_SHORT).show()
            }   else if((newCount >= 1000) && (newCount <= 1050) ){
                // i++
                Toast.makeText(requireContext(),"Congratulations! you have completed 1 goal",Toast.LENGTH_SHORT).show()
            }

            binding.stepsText.text = formatSteps(count)
        }
    }

    private fun updateAggregateMetrics(data: Map<DataType, AggregateDataPoint>) {
        (data[DataType.DISTANCE] as? CumulativeDataPoint)?.let {
            Log.e("ExerciseFragment","DISTANCE ${formatDistanceKm(it.total.asDouble())}")
            val dist = formatDistanceKm(it.total.asDouble()).toString()
            Log.e("ExerciseFragment","dist $dist")
            if(dist == "0.04km"){
                Toast.makeText(requireContext(),"Congratulation you have covered $dist distance",Toast.LENGTH_SHORT).show()
            }
            binding.distanceText.text = formatDistanceKm(it.total.asDouble())
        }
        (data[DataType.TOTAL_CALORIES] as? CumulativeDataPoint)?.let {
            Log.e("ExerciseFragment","TOTAL_CALORIES ${it.total}")
            val cal = formatCalories(it.total.asDouble()).toString()
            Log.e("ExerciseFragment","cal ${cal}")
            if(cal == "2 cal"){
                Toast.makeText(requireContext(),"Congratulation you have burnt $cal ",Toast.LENGTH_SHORT).show()
        }
            binding.caloriesText.text = formatCalories(it.total.asDouble())
        }
//        (data[DataType.FLOORS] as? CumulativeDataPoint)?.let {
//            Log.e("ExerciseFragment","FLOORS ${it.total.asDouble()}")
//            binding.floorText.text = it.total.toString()
//        }
//        (data[DataType.DAILY_STEPS] as? CumulativeDataPoint)?.let {
//            Log.e("ExerciseFragment","DAILY_STEPS@@ ${formatSteps(it.total.asLong())}")
//            binding.stepsText.text = formatSteps(it.total.asLong())
//        }
    }

    private fun updateLaps(laps: Int) {
        //binding.lapsText.text = laps.toString()
    }

    private fun startChronometer() {
        Log.e("ExerciseFragment","startChronometer $CHRONO_TICK_MS")
        if (chronoTickJob == null) {
            chronoTickJob = viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        Log.e("ExerciseFragment","tick $CHRONO_TICK_MS")
                        delay(CHRONO_TICK_MS)
                        updateChronometer()
                    }
                }
            }
        }
    }

    private fun stopChronometer() {
        Log.e("ExerciseFragment","stopChronometer")

        chronoTickJob?.cancel()
        chronoTickJob = null
    }

    private fun updateChronometer() {
        Log.e("ExerciseFragment","updateChronometer")
        // We update the chronometer on our own regular intervals independent of the exercise
        // duration value received. If the exercise is still active, add the difference between
        // the last duration update and now.
        val difference = if (cachedExerciseState == ExerciseState.ACTIVE) {
            Duration.between(activeDurationUpdate.timestamp, Instant.now())
        } else {
            Duration.ZERO
        }
        Log.e("ExerciseFragment","activeDurationUpdate ${activeDurationUpdate.duration}")
        Log.e("ExerciseFragment","difference $difference")


        val duration = activeDurationUpdate.duration + difference
        Log.e("ExerciseFragment","duration $duration")

        binding.elapsedTime.text = formatElapsedTime(duration, !ambientController.isAmbient)
    }

    private fun resetDisplayedFields() {

        Log.e("ExerciseFragment","resetDisplayedFields ")

        getString(R.string.empty_metric).let {
            binding.heartRateText.text = it
            binding.caloriesText.text = it
            binding.distanceText.text = it
          //  binding.lapsText.text = it
           // binding.floorText.text = it
            binding.stepsText.text = it
        }
        binding.elapsedTime.text = formatElapsedTime(Duration.ZERO, true)
    }

    // -- Ambient Mode support

    private fun setAmbientUiState(isAmbient: Boolean) {
        // Change icons to white while in ambient mode.
        val iconTint = if (isAmbient) {
            Color.WHITE
        } else {
            resources.getColor(R.color.primary_orange, null)
        }
        ColorStateList.valueOf(iconTint).let {
            binding.clockIcon.imageTintList = it
            binding.heartRateIcon.imageTintList = it
            binding.caloriesIcon.imageTintList = it
            binding.distanceIcon.imageTintList = it
           // binding.lapsIcon.imageTintList = it
            binding.stepIcon.imageTintList = it
            //binding.floorIcon.imageTintList = it
        }

        // Hide the buttons in ambient mode.
        val buttonVisibility = if (isAmbient) View.INVISIBLE else View.VISIBLE
        buttonVisibility.let {
            binding.startEndButton.visibility = it
            binding.pauseResumeButton.visibility = it
        }
    }

    private fun performOneTimeUiUpdate() {
        val service = checkNotNull(serviceConnection.exerciseService) {
            "Failed to achieve ExerciseService instance"
        }
        updateExerciseStatus(service.exerciseState.value)
        updateMetrics(service.exerciseMetrics.value)
        updateLaps(service.exerciseLaps.value)

        activeDurationUpdate = service.exerciseDurationUpdate.value
        updateChronometer()
    }

    inner class AmbientModeHandler {
        internal fun onAmbientEvent(event: AmbientEvent) {
            when (event) {
                is AmbientEvent.Enter -> onEnterAmbient()
                is AmbientEvent.Exit -> onExitAmbient()
                is AmbientEvent.Update -> onUpdateAmbient()
            }
        }

        private fun onEnterAmbient() {
            // Note: Apps should also handle low-bit ambient and burn-in protection.
          Log.e("ExerciseFragment","onEnterAmbient")

            unbindViewsFromService()
            setAmbientUiState(true)
            performOneTimeUiUpdate()


        }

        private fun onExitAmbient() {
            Log.e("ExerciseFragment","onExitAmbient")

            performOneTimeUiUpdate()
            setAmbientUiState(false)
            bindViewsToService()
        }

        private fun onUpdateAmbient() {
            Log.e("ExerciseFragment","onUpdateAmbient")

            performOneTimeUiUpdate()
        }
    }

    private companion object {
        const val CHRONO_TICK_MS = 200L
    }


    private fun onAmbientSupport(isAmbient: Boolean){






            // Hide the buttons in ambient mode.
            val buttonVisibility = if (isAmbient) View.INVISIBLE else View.VISIBLE
            buttonVisibility.let {
                binding.startEndButton.visibility = it
                binding.pauseResumeButton.visibility = it
                binding.clockIcon.visibility = it
                binding.heartRateIcon.visibility = it
                binding.caloriesIcon.visibility = it
                binding.distanceIcon.visibility = it
                binding.stepIcon.visibility = it
                binding.heartRateText.visibility = it
                binding.stepsText.visibility = it
                binding.caloriesText.visibility = it
                binding.elapsedTime.visibility = it
                binding.distanceText.visibility = it
            }
        }

}
