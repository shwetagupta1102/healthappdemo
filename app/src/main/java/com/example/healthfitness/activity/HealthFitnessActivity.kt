package com.example.healthfitness.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import androidx.wear.ambient.AmbientModeSupport
import com.example.healthfitness.R
import com.example.healthfitness.viewmodel.AmbientEvent
import com.example.healthfitness.viewmodel.HealthFitnessViewModel


@AndroidEntryPoint
class HealthFitnessActivity : AppCompatActivity(R.layout.activity_health_fitness) ,
    AmbientModeSupport.AmbientCallbackProvider {

    private val viewModel: HealthFitnessViewModel by viewModels()


    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_STEM_3,
            KeyEvent.KEYCODE_STEM_PRIMARY -> {
            viewModel.sendKeyPress()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = AmbientModeCallback()

    inner class AmbientModeCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle) {
              viewModel.sendAmbientEvent(AmbientEvent.Enter(ambientDetails))
        }

        override fun onExitAmbient() {
            viewModel.sendAmbientEvent(AmbientEvent.Exit)
        }

        override fun onUpdateAmbient() {
             viewModel.sendAmbientEvent(AmbientEvent.Update)
        }
    }

}


