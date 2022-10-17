package com.example.healthfitness.viewmodel

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthFitnessViewModel @Inject constructor(): ViewModel() {
        private val _ambientEventChannel = Channel<AmbientEvent>(capacity = Channel.CONFLATED)
        val ambientEventFlow = _ambientEventChannel.receiveAsFlow()

        private val _keyPressChannel = Channel<Unit>(capacity = Channel.CONFLATED)
        val keyPressFlow = _keyPressChannel.receiveAsFlow()

        fun sendAmbientEvent(event: AmbientEvent) {
            viewModelScope.launch {
                _ambientEventChannel.send(event)
            }
        }

        fun sendKeyPress() {
            Log.e("HealthFitnessViewModel","onKeyPress")
            viewModelScope.launch {
                _keyPressChannel.send(Unit)
            }
        }
    }

    sealed class AmbientEvent {
        class Enter(val ambientDetails: Bundle) : AmbientEvent()
        object Exit : AmbientEvent()
        object Update : AmbientEvent()
    }