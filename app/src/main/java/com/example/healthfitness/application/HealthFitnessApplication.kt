package com.example.healthfitness.application

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.healthfitness.repository.PassiveGoalsRepository.Companion.PREFERENCES_FILENAME
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HealthFitnessApplication: Application(), Configuration.Provider{
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

const val TAG = "Health&Fitness"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREFERENCES_FILENAME)