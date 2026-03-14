package com.example.treasurewalk.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun PermissionProvider(
    onPermissionsGranted: () -> Unit,
    content: @Composable (requestPermissions: () -> Unit) -> Unit
) {
    val context = LocalContext.current

    // Lista dei permessi necessari
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Launcher per la richiesta permessi
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val isGranted = permissionsMap.values.all { it }
        if (isGranted) {
            onPermissionsGranted()
        } else {
            // Qui potresti mostrare un Toast o un dialogo di errore
        }
    }

    // Funzione che controlla se i permessi sono già attivi
    val checkAndRequest = remember(context, launcher, permissions) {
        { // <-- Attenzione alle doppie graffe: è una lambda che restituisce una lambda
            val hasPermissions = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermissions) {
                onPermissionsGranted()
            } else {
                launcher.launch(permissions)
            }
        }
    }

    // Passiamo la funzione al contenuto (es. il pulsante GIOCA)
    content(checkAndRequest)
}