package com.example.healthfitness.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.services.client.data.*
import com.example.healthfitness.repository.PassiveGoalsRepository
import com.example.healthfitness.servicemanager.dailyStepsGoal
import com.example.healthfitness.servicemanager.floorsGoal
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PassiveGoalsReceiver : BroadcastReceiver() {
    @Inject
    lateinit var repository: PassiveGoalsRepository
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PassiveGoal.ACTION_GOAL) {
            return
        }
        val passiveGoal = PassiveGoal.fromIntent(intent)
        val time = Instant.now()
        if (passiveGoal == floorsGoal) {
            runBlocking {
                Log.e("PaasiveGoalReceiver","floorGoal $floorsGoal")
                repository.updateLatestFloorsGoalTime(time)
            }
        } else if (passiveGoal == dailyStepsGoal) {
            runBlocking {
                Log.e("PaasiveGoalReceiver","dailyStepsGoal $dailyStepsGoal")
                repository.setLatestDailyGoalAchieved(time)
            }
        }
    }
}
