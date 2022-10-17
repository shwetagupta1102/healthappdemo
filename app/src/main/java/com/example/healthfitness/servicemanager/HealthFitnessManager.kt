package com.example.healthfitness.servicemanager


import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.ExerciseUpdateListener
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.data.*
import com.example.healthfitness.application.TAG
import com.example.healthfitness.receiver.PassiveGoalsReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject







// Goals we want to create.
val dailyStepsGoal by lazy {
    // 10000 steps per day
    val condition = DataTypeCondition(
        dataType = DataType.DAILY_STEPS,
        threshold = Value.ofLong(20),
        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
    )
    // For DAILY_* DataTypes, REPEATED goals trigger only once per 24 period, resetting each day
    // at midnight local time.
    PassiveGoal(condition, PassiveGoal.TriggerType.REPEATED)
}

val floorsGoal by lazy {
    // A goal that triggers for every 3 floors climbed.
    val condition = DataTypeCondition(
        dataType = DataType.FLOORS,
        threshold = Value.ofDouble(1.0),
        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
    )
    // REPEATED means we will be notified on every occurrence until we unsubscribe.
    PassiveGoal(condition, PassiveGoal.TriggerType.REPEATED)
}



class HealthFitnessManager @Inject constructor(
    healthServicesClient: HealthServicesClient,
    coroutineScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) {

    private val exerciseClient = healthServicesClient.exerciseClient
    private val passiveMonitoringClient = healthServicesClient.passiveMonitoringClient
    private val measureClient = healthServicesClient.measureClient


    private var exerciseCapabilities: ExerciseTypeCapabilities? = null
   // private var passiveMonitoringCapabilities = passiveMonitoringClient.capabilities
    private var measureCapabilities = measureClient.capabilities

    private var capabilitiesLoaded = false



    suspend fun hasFloorsAndDailyStepsCapability(): Boolean {
        val capabilities = passiveMonitoringClient.capabilities.await()
        return capabilities.supportedDataTypesEvents.containsAll(
            setOf(
                DataType.TOTAL_CALORIES,
                DataType.FLOORS
            )
        )
    }



    suspend fun subscribeForGoals() {
        Log.e(TAG, "Subscribing for goals")
       val componentName = ComponentName(context, PassiveGoalsReceiver::class.java)
        // Each goal is a separate subscription.
        passiveMonitoringClient.registerPassiveGoalCallback(dailyStepsGoal, componentName).await()
        passiveMonitoringClient.registerPassiveGoalCallback(floorsGoal, componentName).await()
    }

    suspend fun unsubscribeFromGoals() {
        Log.e(TAG, "Unsubscribing from goals")
        passiveMonitoringClient.unregisterPassiveGoalCallback(dailyStepsGoal).await()
        passiveMonitoringClient.unregisterPassiveGoalCallback(floorsGoal).await()
    }

    suspend fun prepareExercise() {
        Log.d(TAG, "Preparing an exercise")

        val warmUpConfig = WarmUpConfig.builder()
            .setExerciseType(ExerciseType.RUNNING)
            .setDataTypes(
                setOf(
                    DataType.HEART_RATE_BPM,
                    DataType.LOCATION,
                   // DataType.DAILY_STEPS,
                    DataType.STEPS,
                   // DataType.FLOORS,
                    //DataType.DAILY_FLOORS
                )
            )
            .build()

        try {
            exerciseClient.prepareExercise(warmUpConfig).await()
        } catch (e: Exception) {
            Log.e(TAG, "Prepare exercise failed - ${e.message}")
        }
    }

    suspend fun endExercise() {
        Log.d(TAG, "Ending exercise")
        exerciseClient.endExercise().await()
    }

    suspend fun pauseExercise() {
        Log.d(TAG, "Pausing exercise")
        exerciseClient.pauseExercise().await()
    }

    suspend fun resumeExercise() {
        Log.d(TAG, "Resuming exercise")
        exerciseClient.resumeExercise().await()
    }

    suspend fun markLap() {
        if (isExerciseInProgress()) {
            exerciseClient.markLap().await()
        }
    }
    suspend fun hasExerciseCapability(): Boolean {
        return getExerciseCapabilities() != null
    }

    suspend fun getExerciseCapabilities(): ExerciseTypeCapabilities? {
        if (!capabilitiesLoaded) {
            val capabilities = exerciseClient.capabilities.await()
            if (ExerciseType.RUNNING in capabilities.supportedExerciseTypes) {
                exerciseCapabilities =
                    capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING)
            }
            capabilitiesLoaded = true
        }
        return exerciseCapabilities
    }



    suspend fun isExerciseInProgress(): Boolean {
        val exerciseInfo = exerciseClient.currentExerciseInfo.await()
        return exerciseInfo.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS
    }

    suspend fun isTrackingExerciseInAnotherApp(): Boolean {
        val exerciseInfo = exerciseClient.currentExerciseInfo.await()
        return exerciseInfo.exerciseTrackedStatus == ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
    }

    /***
     * Note: don't call this method from outside of foreground service (ie. [ExerciseService])
     * when acquiring calories or distance.
     */
    suspend fun startExercise() {
        Log.e(TAG, "Starting exercise")
        // Types for which we want to receive metrics. Only ask for ones that are supported.
        val capabilities = getExerciseCapabilities() ?: return
        val dataTypes = setOf(
            DataType.HEART_RATE_BPM,
            DataType.STEPS,
            DataType.LOCATION
        ).intersect(capabilities.supportedDataTypes)

        val aggDataTypes = setOf(
            DataType.TOTAL_CALORIES,
            DataType.DISTANCE
        ).intersect(capabilities.supportedDataTypes)

        val exerciseGoals = mutableListOf<ExerciseGoal>()
        if (supportsCalorieGoal(capabilities)) {
            // Create a one-time goal.
            exerciseGoals.add(
                ExerciseGoal.createOneTimeGoal(
                    DataTypeCondition(
                        dataType = DataType.TOTAL_CALORIES,
                        threshold = Value.ofDouble(CALORIES_THRESHOLD),
                        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
                    )
                )
            )
        }

        if (supportsDistanceMilestone(capabilities)) {
            // Create a milestone goal. To make a milestone for every kilometer, set the initial
            // threshold to 1km and the period to 1km.
            exerciseGoals.add(
                ExerciseGoal.createMilestone(
                    condition = DataTypeCondition(
                        dataType = DataType.DISTANCE,
                        threshold = Value.ofDouble(DISTANCE_THRESHOLD),
                        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
                    ),
                    period = Value.ofDouble(DISTANCE_THRESHOLD)
                )
            )
        }


        if (supportStepsGoal(capabilities)) {
            exerciseGoals.add(
                ExerciseGoal.createMilestone(
                    condition = DataTypeCondition(
                        dataType = DataType.STEPS,
                        threshold = Value.ofLong(1000),
                        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
                    ),
                    period = Value.ofLong(1000)

                )
            )
        }
//
//        if (supportFloorsGoal(capabilities)) {
//            exerciseGoals.add(
//                ExerciseGoal.createOneTimeGoal(
//                    condition = DataTypeCondition(
//                        dataType = DataType.FLOORS,
//                        threshold = Value.ofDouble(100.0),
//                        comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
//                    )
//
//                )
//            )
//        }

        val config = ExerciseConfig.builder()
            .setExerciseType(ExerciseType.RUNNING)
            .setShouldEnableAutoPauseAndResume(false)
            .setAggregateDataTypes(aggDataTypes)
            .setDataTypes(dataTypes)
            .setExerciseGoals(exerciseGoals)
            // Required for GPS for LOCATION data type, optional for some other types.
            .setShouldEnableGps(true)
            .build()
        exerciseClient.startExercise(config).await()
    }

    private fun supportsCalorieGoal(capabilities: ExerciseTypeCapabilities): Boolean {
        val supported = capabilities.supportedGoals[DataType.TOTAL_CALORIES]
        return supported != null && ComparisonType.GREATER_THAN_OR_EQUAL in supported
    }

    private fun supportsDistanceMilestone(capabilities: ExerciseTypeCapabilities): Boolean {
        val supported = capabilities.supportedMilestones[DataType.DISTANCE]
        return supported != null && ComparisonType.GREATER_THAN_OR_EQUAL in supported
    }

    private fun supportStepsGoal(capabilities: ExerciseTypeCapabilities): Boolean {
        val stepGoals = capabilities.supportedGoals[DataType.DAILY_STEPS]
        Log.e("HealthFitnessManager","StepGoals $stepGoals")
        val supportsAutoPause = capabilities.supportsAutoPauseAndResume
        Log.e("HealthFitnessManager","supportsAutoPause $supportsAutoPause")
        return    stepGoals != null && ComparisonType.GREATER_THAN_OR_EQUAL in stepGoals

    }

    private fun supportFloorsGoal(capabilities: ExerciseTypeCapabilities): Boolean {
        val floorGoals = capabilities.supportedGoals[DataType.FLOORS]
        Log.e("HealthFitnessManager","floorGoals $floorGoals")
        val supportsAutoPause = capabilities.supportsAutoPauseAndResume
        Log.e("HealthFitnessManager","supportsAutoPause $supportsAutoPause")
        return    floorGoals != null && ComparisonType.GREATER_THAN_OR_EQUAL in floorGoals

    }

    private companion object {
        const val CALORIES_THRESHOLD = 250.0
        const val DISTANCE_THRESHOLD = 1_000.0 // meters


    }
    sealed class ExerciseMessage {
        class ExerciseUpdateMessage(val exerciseUpdate: ExerciseUpdate) : ExerciseMessage()
        class LapSummaryMessage(val lapSummary: ExerciseLapSummary) : ExerciseMessage()
        class LocationAvailabilityMessage(val locationAvailability: LocationAvailability) :
            ExerciseMessage()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val exerciseUpdateFlow = callbackFlow<ExerciseMessage> {
        val listener = object : ExerciseUpdateListener {
            override fun onExerciseUpdate(update: ExerciseUpdate) {
                coroutineScope.runCatching {
                    trySendBlocking(ExerciseMessage.ExerciseUpdateMessage(update))
                }
            }

            override fun onLapSummary(lapSummary: ExerciseLapSummary) {
                coroutineScope.runCatching {
                    trySendBlocking(ExerciseMessage.LapSummaryMessage(lapSummary))
                }
            }

            override fun onAvailabilityChanged(dataType: DataType, availability: Availability) {
                if (availability is LocationAvailability) {
                    coroutineScope.runCatching {
                        trySendBlocking(ExerciseMessage.LocationAvailabilityMessage(availability))
                    }
                }
            }
        }
        exerciseClient.setUpdateListener(listener)
        awaitClose {
            exerciseClient.clearUpdateListener(listener)
        }
    }


}