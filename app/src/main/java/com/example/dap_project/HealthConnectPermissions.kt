package com.example.dap_project

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

// Define the Health Connect provider package name
const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

/**
 * Creates and remembers a launcher for Health Connect permissions.
 *
 * @param onResult Callback invoked with a set of granted permission strings.
 */
@Composable
fun rememberHealthConnectPermissionLauncher(
    onResult: (Set<String>) -> Unit
): ActivityResultLauncher<Set<String>> { // Ensure ActivityResultLauncher is imported
    val activity = LocalContext.current as? ComponentActivity
        ?: throw IllegalStateException("rememberHealthConnectPermissionLauncher must be called from an Activity context")

    return rememberLauncherForActivityResult( // Ensure this is imported from androidx.activity.compose
        contract = PermissionController.createRequestPermissionResultContract(), // From androidx.health.connect.client
        onResult = onResult
    )
}

/**
 * Opens the Play Store to install or update Health Connect.
 *
 * @param context The current context.
 * @param status The SDK status code from HealthConnectClient.getSdkStatus().
 */
fun openHealthConnectInstallation(context: Context, status: Int) {
    val uriString = when (status) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, // Comma for multiple cases
        HealthConnectClient.SDK_UNAVAILABLE ->
            // This is the recommended URI to take users to onboarding or installation
            "market://details?id=$PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding"
        // Consider if other statuses should also lead to Play Store, though these two are primary.
        else -> {
            Log.w("HealthConnect", "openHealthConnectInstallation called with unexpected status: $status")
            null
        }
    }

    if (uriString != null) {
        try {
            Log.d("HealthConnect", "Attempting to open Play Store with URI: $uriString")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            intent.setPackage("com.android.vending") // Explicitly target Play Store
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("HealthConnect", "Play Store (com.android.vending) not found: ${e.message}")
            // Fallback: Try without explicitly setting the package,
            // which might work if a different app store can handle market:// links.
            try {
                Log.d("HealthConnect", "Attempting to open market URI without explicit Play Store package: $uriString")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                context.startActivity(fallbackIntent)
            } catch (e2: ActivityNotFoundException) {
                Log.e("HealthConnect", "Failed to open any app store for URI: $uriString. Error: ${e2.message}")
                // Inform the user that the Play Store could not be opened (e.g., via a Toast)
                android.widget.Toast.makeText(context, "Could not open the app store. Please install or update Health Connect manually.", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Catch any other unexpected exceptions
            Log.e("HealthConnect", "Unexpected error when trying to open Play Store: ${e.message}")
            android.widget.Toast.makeText(context, "An error occurred while trying to open Health Connect.", android.widget.Toast.LENGTH_LONG).show()
        }
    } else {
        Log.d("HealthConnect", "No URI generated for Play Store, status was: $status")
    }
}