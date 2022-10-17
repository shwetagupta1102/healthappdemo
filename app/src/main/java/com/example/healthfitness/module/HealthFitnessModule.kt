package com.example.healthfitness.module

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import com.example.healthfitness.application.dataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class HealthFitnessModule {
    @Singleton
    @Provides
    fun providesHealthServicesClient(@ApplicationContext context: Context):HealthServicesClient =
        HealthServices.getClient(context)
    @Singleton
    @Provides
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Singleton
    @Provides
    fun provideDataStore(@ApplicationContext context: Context) = context.dataStore

}