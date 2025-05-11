package com.example.dap_project

// Android and Core Jetpack/Kotlin imports
import android.Manifest // For permission strings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

// Health Connect imports
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
// Assuming your HealthConnect helpers (PROVIDER_PACKAGE_NAME, openHealthConnectInstallation, rememberHealthConnectPermissionLauncher)
// are in the same package or correctly imported.
// If they are in HealthConnectPermissions.kt in this package, no explicit import is needed for them.

// Location imports
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Chaquopy Python import
import com.chaquo.python.Python

// Coroutines and Time imports
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberComboBoxScreen() {
    // Common state variables
    val options = listOf("-3", "-2", "-1", "0", "1", "2", "3")
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(options[3]) } // Default to "0"
    var selectedNumber by remember { mutableStateOf<Int?>(options[3].toIntOrNull()) }
    var pythonResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Health Connect State ---
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(context) }
    var hcSdkStatus: Int by remember { mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE) }
    val hcPermissionsToRequest = remember {
        setOf(HealthPermission.getReadPermission(HeartRateRecord::class))
    }
    var hcGrantedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allHcPermissionsGranted = remember(hcGrantedPermissions) {
        hcGrantedPermissions.containsAll(hcPermissionsToRequest)
    }
    var showHcNotAvailableDialog by remember { mutableStateOf(false) }
    var hcNotAvailableDialogStatusValue by remember { mutableStateOf(0) } // Renamed to avoid conflict
    var showHcPermissionRationaleDialog by remember { mutableStateOf(false) }
    var heartRateData by remember { mutableStateOf<List<HeartRateRecord>>(emptyList()) }
    var isLoadingHeartRate by remember { mutableStateOf(false) }

    // --- Location State ---
    var userLocationText by remember { mutableStateOf("Location: Not fetched yet") } // Renamed
    var showLocationPermissionRationaleDialog by remember { mutableStateOf(false) } // Renamed
    var locationPermissionsAreGranted by remember { mutableStateOf(false) } // Renamed
    val fusedLocationClient: FusedLocationProviderClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var latitude = 0f
    var longitude = 0f

    // Location Callback
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    userLocationText = "Lat: ${"%.4f".format(location.latitude)}, Lon: ${"%.4f".format(location.longitude)}"
                    latitude = location.latitude.toFloat()
                    longitude = location.longitude.toFloat()
                    Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude}")
                    // Remove updates after getting one location if that's all you need
                    fusedLocationClient.removeLocationUpdates(this)
                } ?: run {
                    userLocationText = "Location: Unable to get current location."
                    Log.d("LocationUpdate", "Last location in result is null")
                }
            }
        }
    }

    // --- Helper Functions ---
    fun checkLocationPermissions() {
        locationPermissionsAreGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("LocationPermission", "Checked location permissions: $locationPermissionsAreGranted")
    }


    // --- Permission Launchers ---
    val healthConnectPermissionLauncher = rememberHealthConnectPermissionLauncher { receivedPermissions ->
        hcGrantedPermissions = receivedPermissions
        if (receivedPermissions.containsAll(hcPermissionsToRequest)) {
            Log.d("HealthConnect", "All required HC permissions granted by user.")
        } else {
            Log.w("HealthConnect", "Not all required HC permissions were granted. Requested: $hcPermissionsToRequest, Received: $receivedPermissions")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        var allLocGranted = true
        permissionsResult.forEach { (permission, granted) ->
            Log.d("LocationPermission", "Permission $permission granted: $granted")
            if (!granted) allLocGranted = false
        }
        locationPermissionsAreGranted = allLocGranted
        if (allLocGranted) {
            userLocationText = "Location: Permissions Granted. Fetching..."
            requestCurrentLocation(context, fusedLocationClient, locationCallback) { errorMsg ->
                userLocationText = errorMsg
            }
        } else {
            userLocationText = "Location: Permissions Denied by user."
        }
    }

    // --- Initial Effect Hooks ---
    LaunchedEffect(Unit) {
        // Health Connect SDK Status Check
        hcSdkStatus = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)
        Log.d("HealthConnectInit", "Initial HC sdkStatus: $hcSdkStatus")
        if (hcSdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            hcGrantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d("HealthConnectInit", "Initially granted HC permissions: $hcGrantedPermissions")
        } else {
            hcNotAvailableDialogStatusValue = hcSdkStatus
            showHcNotAvailableDialog = true
            Log.d("HealthConnectInit", "HC SDK not available. Status: $hcSdkStatus. Dialog will be shown.")
        }

        // Location Permissions Check
        checkLocationPermissions()
    }

    // --- UI Dialogs ---
    if (showHcNotAvailableDialog) {
        AlertDialog(
            onDismissRequest = { showHcNotAvailableDialog = false },
            title = { Text("Health Connect Not Available") },
            text = {
                val message = when (hcNotAvailableDialogStatusValue) {
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Please update Health Connect."
                    HealthConnectClient.SDK_UNAVAILABLE -> "Please install Health Connect."
                    else -> "Health Connect is not available (Status: $hcNotAvailableDialogStatusValue)."
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    showHcNotAvailableDialog = false
                    openHealthConnectInstallation(context, hcNotAvailableDialogStatusValue)
                }) { Text("Open Play Store") }
            },
            dismissButton = { TextButton(onClick = { showHcNotAvailableDialog = false }) { Text("Dismiss") } }
        )
    }

    if (showHcPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showHcPermissionRationaleDialog = false },
            title = { Text("Health Connect Permission") },
            text = { Text("This app needs permission to read your heart rate data from Health Connect.") },
            confirmButton = {
                TextButton(onClick = {
                    showHcPermissionRationaleDialog = false
                    healthConnectPermissionLauncher.launch(hcPermissionsToRequest)
                }) { Text("Grant HC Permission") }
            },
            dismissButton = { TextButton(onClick = { showHcPermissionRationaleDialog = false }) { Text("Cancel") } }
        )
    }

    if (showLocationPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showLocationPermissionRationaleDialog = false },
            title = { Text("Location Permission Required") },
            text = { Text("This app needs access to your location for location-based features.") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPermissionRationaleDialog = false
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text("Grant Location Permission") }
            },
            dismissButton = { TextButton(onClick = { showLocationPermissionRationaleDialog = false }) { Text("Cancel") } }
        )
    }

    // --- Main Screen UI ---
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Added for scrollability
            horizontalAlignment = Alignment.CenterHorizontally,
            // verticalArrangement = Arrangement.Center // Removed for better scrolling with more content
        ) {
            // Thermal Comfort Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedOptionText,
                    onValueChange = {},
                    label = { Text("Select thermal comfort (-3 cold to 3 hot)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedOptionText = selectionOption
                                selectedNumber = selectionOption.toIntOrNull()
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            if (selectedNumber != null) {
                Text(text = "You selected thermal comfort: $selectedNumber")
            }
            Spacer(modifier = Modifier.height(24.dp)) // Increased spacing

            // --- Location UI ---
            Text(text = userLocationText, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                checkLocationPermissions()
                if (locationPermissionsAreGranted) {
                    userLocationText = "Location: Fetching..."
                    requestCurrentLocation(context, fusedLocationClient, locationCallback) { errorMsg ->
                        userLocationText = errorMsg
                    }
                } else {
                    showLocationPermissionRationaleDialog = true
                }
            }) {
                Text("Get My Coordinates")
            }
            Spacer(modifier = Modifier.height(24.dp)) // Increased spacing

            // --- Health Connect UI ---
            if (hcSdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                if (!allHcPermissionsGranted) {
                    Button(onClick = { showHcPermissionRationaleDialog = true }) {
                        Text("Grant Heart Rate Permission")
                    }
                } else {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingHeartRate = true
                                try {
                                    val endTime = Instant.now()
                                    val startTime = endTime.minusSeconds(24 * 60 * 60) // Last 24 hours
                                    val request = ReadRecordsRequest(
                                        recordType = HeartRateRecord::class,
                                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                                    )
                                    val response = healthConnectClient.readRecords(request)
                                    heartRateData = response.records
                                    Log.d("HealthConnectData", "Heart rate records fetched: ${heartRateData.size}")
                                } catch (e: Exception) {
                                    Log.e("HealthConnectData", "Error reading heart rate data", e)
                                    // Consider showing a Toast to the user
                                } finally {
                                    isLoadingHeartRate = false
                                }
                            }
                        },
                        enabled = !isLoadingHeartRate
                    ) {
                        Text(if (isLoadingHeartRate) "Loading HR..." else "Fetch Heart Rate (Last 24h)")
                    }
                    if (heartRateData.isNotEmpty()) {
                        Text("Fetched ${heartRateData.size} heart rate records.", style = MaterialTheme.typography.bodySmall)
                        heartRateData.take(3).forEach { record -> // Show a few samples
                            val averageBpm = record.samples.map { it.beatsPerMinute }.average()
                            Text("Avg BPM: ${"%.1f".format(averageBpm)} at ${ZonedDateTime.ofInstant(record.startTime, ZoneId.systemDefault()).toLocalTime()}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (!isLoadingHeartRate && allHcPermissionsGranted) {
                        Text("No heart rate data found for the last 24 hours or permission recently granted.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("Health Connect is not available or needs setup.")
                Button(onClick = { showHcNotAvailableDialog = true }) {
                    Text("Setup Health Connect")
                }
            }
            Spacer(modifier = Modifier.height(24.dp)) // Increased spacing

            // --- Python Service Button ---
            Button(onClick = {
                if (selectedNumber != null) {
                    coroutineScope.launch {
                        pythonResult = submitNumberToService(selectedNumber!!, latitude, longitude, heartRateData)
                    }
                } else {
                    // Handle case where no number is selected, perhaps show a Toast
                    Log.w("PythonSubmit", "No thermal comfort number selected.")
                }
            }) {
                Text("Submit Data to Python Service")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (pythonResult != null) {
                Text(text = "Python Service Result: $pythonResult", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(32.dp)) // Extra space at the bottom
        }
    }
}

// Updated to accept location and heart rate data
suspend fun submitNumberToService(
    thermalComfort: Int,
    latitude: Float,
    longitude: Float,
    hrData: List<HeartRateRecord> // Pass the fetched heart rate records
): String {
    Log.d("SubmitService", "Submitting Thermal Comfort: $thermalComfort, Location: $latitude, $longitude, HR Records: ${hrData.size}")

    val py = Python.getInstance()
    val module = py.getModule("plot") // Ensure this Python module exists in app/src/main/python/
    val currentDate: LocalDate = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd") // Corrected: yyyy for year
    val formattedDate: String = currentDate.format(dateFormatter)
    val currentHour: Int = getCurrentHourWithTimeZone()

    val mostRecentBpmValue: Long = if (hrData.isNotEmpty()) {
        val latestSample = hrData
            .flatMap { it.samples } // Get all samples from all records
            .filterNotNull() // Ensure samples are not null
            .maxByOrNull { it.time } // Find the sample with the latest time
        latestSample?.beatsPerMinute ?: 0
    } else {
        0
    }
    val formattedAverageHeartRate = if (mostRecentBpmValue == 0L) "N/A" else "%.1f".format(mostRecentBpmValue)


    Log.d("SubmitService", "Calling Python 'plot' with Date: $formattedDate, Hour: $currentHour, Latitude: $latitude, Longitude: $longitude, AvgHR: $formattedAverageHeartRate, ThermalComfort: $thermalComfort")

    val result = try {
        module.callAttr("plot", formattedDate, currentHour, latitude, longitude, mostRecentBpmValue, thermalComfort).toString()
    } catch (e: Exception) {
        Log.e("PythonError", "Error calling Python script 'plot': ${e.message}", e)
        "Error: Python script execution failed. Check logs."
    }

    Log.d("PythonResult", "Result from Python: $result")
    return result
}

fun getCurrentHourWithTimeZone(): Int {
    val zoneId: ZoneId = ZoneId.systemDefault()
    val currentDateTime: ZonedDateTime = ZonedDateTime.now(zoneId)
    return currentDateTime.hour
}

fun requestCurrentLocation(
    ctx: Context,
    client: FusedLocationProviderClient,
    callback: LocationCallback,
    onError: (String) -> Unit
) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) {
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            onError("Location: Please enable location services on your device.")
            Log.w("LocationRequest", "Location services are disabled.")
            return
        }

        Log.d("LocationRequest", "Requesting location updates...")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // Interval 10 seconds
            .setWaitForAccurateLocation(false) // Returns immediately with best available, then improves
            .setMinUpdateIntervalMillis(5000L) // Minimum interval 5 seconds
            .setMaxUpdateDelayMillis(15000L) // Maximum delay 15 seconds
            .build()
        try {
            client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationError", "SecurityException requesting updates: ${e.message}", e)
            onError("Location: Security Exception during request.")
        } catch (e: Exception) {
            Log.e("LocationError", "General Exception requesting updates: ${e.message}", e)
            onError("Location: Error requesting updates.")
        }
    } else {
        Log.w("LocationRequest", "Permissions not granted to request updates.")
        onError("Location: Permissions not granted.")
    }
}