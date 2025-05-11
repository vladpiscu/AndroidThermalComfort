import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.chaquo.python.Python
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.dap_project.PROVIDER_PACKAGE_NAME
import com.example.dap_project.openHealthConnectInstallation
import com.example.dap_project.rememberHealthConnectPermissionLauncher
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberComboBoxScreen() {
    val options = listOf("-3", "-2", "-1", "0", "1", "2", "3")
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(options[0]) }
    var selectedNumber by remember { mutableStateOf<Int?>(options[0].toIntOrNull()) }
    var pythonResult by remember { mutableStateOf<String?>(null) } // State to store Python result
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Health Connect State ---
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(context) }
    var sdkStatus: Int by remember { mutableStateOf<Int>(HealthConnectClient.SDK_UNAVAILABLE) }
    val permissionsToRequest = remember {
        setOf(HealthPermission.getReadPermission(HeartRateRecord::class))
    }
    var grantedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allPermissionsGranted = remember(grantedPermissions) {
        grantedPermissions.containsAll(permissionsToRequest)
    }

    var showHcNotAvailableDialog by remember { mutableStateOf(false) }
    var hcNotAvailableDialogStatus by remember { mutableStateOf(0) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var heartRateData by remember { mutableStateOf<List<HeartRateRecord>>(emptyList()) }
    var isLoadingHeartRate by remember { mutableStateOf(false) }


    // Launcher for Health Connect permissions
    val permissionLauncher = rememberHealthConnectPermissionLauncher { receivedPermissions ->
        grantedPermissions = receivedPermissions // Update state with all permissions granted by the dialog
        if (receivedPermissions.containsAll(permissionsToRequest)) {
            Log.d("HealthConnect", "All required permissions granted by user.")
        } else {
            Log.w("HealthConnect", "Not all required permissions were granted. Requested: $permissionsToRequest, Actually Received: $receivedPermissions")
            // You might want to show a message that some features will be limited
        }
    }

    // Check Health Connect SDK status and initial permissions
    LaunchedEffect(Unit) {
        sdkStatus = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            if (!grantedPermissions.containsAll(permissionsToRequest)) {
                Log.d("HealthConnect", "SDK available, but not all permissions initially granted.")
            } else {
                Log.d("HealthConnect", "SDK available and all required permissions already granted.")
            }
        } else {
            hcNotAvailableDialogStatus = sdkStatus
            showHcNotAvailableDialog = true
            Log.d("HealthConnect", "SDK not available. Status: $sdkStatus")
        }
    }

    // --- UI Dialogs for Health Connect ---
    if (showHcNotAvailableDialog) {
        AlertDialog(
            onDismissRequest = { showHcNotAvailableDialog = false },
            title = { Text("Health Connect Not Available") },
            text = {
                val message = when (hcNotAvailableDialogStatus) {
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                        "Please update the Health Connect app to use this feature."
                    HealthConnectClient.SDK_UNAVAILABLE ->
                        "Please install the Health Connect app to use this feature."
                    else -> "Health Connect is not available on this device (Status: $hcNotAvailableDialogStatus)."
                }
                Text(message)
            },
            confirmButton = {
                TextButton(onClick = {
                    showHcNotAvailableDialog = false
                    Log.d("HealthConnectUI", "Play Store button clicked. Status: $hcNotAvailableDialogStatus")
                    openHealthConnectInstallation(context, hcNotAvailableDialogStatus)
                }) { Text("Open Play Store") }
            },
            dismissButton = {
                TextButton(onClick = { showHcNotAvailableDialog = false }) { Text("Dismiss") }
            }
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Permission Required") },
            text = { Text("This app needs permission to read your heart rate data from Health Connect to provide relevant insights.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    permissionLauncher.launch(permissionsToRequest) // Launch system dialog
                }) { Text("Grant Permission") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                TextField(
                    // The `menuAnchor` modifier must be passed to the text field for correctness.
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedOptionText,
                    onValueChange = {},
                    label = { Text("Select your thermal comfort (-3 - cold to 3 - hot)") },
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
                Text(text = "You selected: $selectedNumber")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Health Connect UI Elements ---
            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                if (!allPermissionsGranted) {
                    Button(onClick = {
                        showPermissionRationaleDialog = true // Show your rationale first
                    }) {
                        Text("Grant Heart Rate Permission")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingHeartRate = true
                                try {
                                    val endTime = Instant.now() // Use Instant for Health Connect
                                    val startTime = endTime.minusSeconds(24 * 60 * 60) // Last 24 hours

                                    val request = ReadRecordsRequest(
                                        recordType = HeartRateRecord::class,
                                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                                    )
                                    val response = healthConnectClient.readRecords(request)
                                    heartRateData = response.records
                                    Log.d("HealthConnect", "Heart rate records fetched: ${heartRateData.size}")
                                } catch (e: Exception) {
                                    Log.e("HealthConnect", "Error reading heart rate data", e)
                                    // Show a Toast or Snackbar to the user
                                } finally {
                                    isLoadingHeartRate = false
                                }
                            }
                        },
                        enabled = !isLoadingHeartRate
                    ) {
                        Text(if (isLoadingHeartRate) "Loading HR..." else "Fetch Heart Rate Data (Last 24h)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (heartRateData.isNotEmpty()) {
                        Text("Fetched ${heartRateData.size} heart rate records.")
                        // Display a few records as an example
                        heartRateData.take(3).forEach { record ->
                            val averageBpm = record.samples.map { it.beatsPerMinute }.average()
                            Text("Avg BPM: ${String.format("%.1f", averageBpm)} at ${ZonedDateTime.ofInstant(record.startTime, ZoneId.systemDefault()).toLocalTime()}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (!isLoadingHeartRate && allPermissionsGranted) {
                        Text("No heart rate data found for the last 24 hours or permission recently granted (try fetching again).")
                    }
                }
            } else {
                Text("Health Connect is not available on this device or needs setup.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showHcNotAvailableDialog = true }) {
                    Text("Setup Health Connect")
                }
            }
            // --- End Health Connect UI Elements ---

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { // Python service button
                if (selectedNumber != null) {
                    coroutineScope.launch {
                        pythonResult = submitNumberToService(selectedNumber!!)
                    }
                }
            }) {
                Text("Submit")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pythonResult != null) {
                Text(text = "Result: $pythonResult")
            }
        }
    }
}

// Placeholder function for submitting the number to another service.
suspend fun submitNumberToService(number: Int): String {
    // Replace this with your actual submission logic.
    // This could involve:
    // 1. Making a network request to a REST API.
    // 2. Storing the data in a database.
    // 3. Sending the data to a local service.
    // 4. Any other action you need to perform.
    Log.d("SubmitNumber", "Submitting number: $number")
    val py = Python.getInstance()
    val module = py.getModule("plot")
    val currentDate: LocalDate = LocalDate.now()
    // Formatting LocalDate:
    val dateFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd")
    val formattedDate: String = currentDate.format(dateFormatter)
    val currentHour: Int = getCurrentHourWithTimeZone()
    println("Current Hour: $currentHour")
    val result = module.callAttr("plot", formattedDate, currentHour).toString()

    Log.d("Result", "Result: $result")
    // You can add a Toast message, a Snackbar, or any other form of feedback to let the user know that the number has been submitted.
    return result
}

fun getCurrentHourWithTimeZone(): Int {
    val zoneId: ZoneId = ZoneId.systemDefault()// Or, specify a time zone: ZoneId.of("America/New_York")
    val currentDateTime: ZonedDateTime = ZonedDateTime.now(zoneId)
    return currentDateTime.hour
}