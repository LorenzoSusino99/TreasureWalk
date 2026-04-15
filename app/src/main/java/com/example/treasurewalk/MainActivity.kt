package com.example.treasurewalk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.treasurewalk.services.WalkTrackingService
import com.example.treasurewalk.ui.features.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppNavigation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safety net: ferma il servizio di tracking quando l'Activity viene distrutta.
        // Questo copre scenari come: app chiusa normalmente, crash, sistema che uccide il processo.
        stopService(Intent(this, WalkTrackingService::class.java))
    }
}