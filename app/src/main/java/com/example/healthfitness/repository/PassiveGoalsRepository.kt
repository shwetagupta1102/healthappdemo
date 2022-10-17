
package com.example.healthfitness.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import javax.inject.Inject

/**
 * Stores the latest information and whether or not passive goals are enabled.
 */
class PassiveGoalsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        const val PREFERENCES_FILENAME = "passive_goals_prefs"
        private val LATEST_DAILY_GOAL_ACHIEVED_TIME = longPreferencesKey("latest_daily_goal_time")
        private val PASSIVE_GOALS_ENABLED = booleanPreferencesKey("passive_goals_enabled")
        private val LATEST_FLOOR_GOAL_TIME = longPreferencesKey("latest_floor_goal_time")
    }

    val passiveGoalsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PASSIVE_GOALS_ENABLED] ?: false
    }

    suspend fun setPassiveGoalsEnabled(enabled: Boolean) {
       Log.e("PassiveGoalsRepo","setPassiveGoalsEnabled $enabled")
        dataStore.edit { prefs ->
            prefs[PASSIVE_GOALS_ENABLED] = enabled
        }
    }

    suspend fun setLatestDailyGoalAchieved(timestamp: Instant) {
        Log.e("PassiveGoalsRepo","setLatestDailyGoalAchieved $timestamp")
        dataStore.edit { prefs ->
            prefs[LATEST_DAILY_GOAL_ACHIEVED_TIME] = timestamp.toEpochMilli()
        }
    }

    val dailyStepsGoalAchieved: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LATEST_DAILY_GOAL_ACHIEVED_TIME]?.let {
            val zoneId = TimeZone.getDefault().toZoneId()
            val achievedDate =
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it), zoneId
                ).toLocalDate()
            achievedDate == LocalDate.now(zoneId)
        } ?: false
    }

    suspend fun updateLatestFloorsGoalTime(timestamp: Instant) {
        dataStore.edit { prefs ->
            Log.e("PassiveGoalsRepository","updateLatestFloorsGoalTime ${timestamp.toEpochMilli()}")
            prefs[LATEST_FLOOR_GOAL_TIME] = timestamp.toEpochMilli()
        }
    }

    val latestFloorsGoalTime: Flow<Instant> = dataStore.data.map { prefs ->
        val time = prefs[LATEST_FLOOR_GOAL_TIME] ?: 0L
        Log.e("PassiveGoalsRepository","time $time")
        Instant.ofEpochMilli(time)
    }
}