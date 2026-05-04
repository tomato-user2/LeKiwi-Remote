package com.lerobot.lekiwiremote

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lerobot.lekiwiremote.model.AppUiState
import com.lerobot.lekiwiremote.model.ConnectionSettings
import com.lerobot.lekiwiremote.model.RotationDirection
import com.lerobot.lekiwiremote.model.SpeedMode
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LeKiwiRemoteApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LeKiwiRemoteApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val screenScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LeKiwi Remote") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(screenScroll)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusCard(uiState = uiState)

            Text(
                text = "Drive",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                JoystickPad(
                    normalizedX = uiState.joystickX,
                    normalizedY = uiState.joystickY,
                    onValueChange = viewModel::updateJoystick,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Rotate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HoldButton(
                        modifier = Modifier.weight(1.0f),
                        label = "Rotate Left",
                        active = uiState.rotationDirection == RotationDirection.LEFT,
                        onPressChanged = { pressed ->
                            viewModel.setRotation(if (pressed) RotationDirection.LEFT else null)
                        },
                    )
                    HoldButton(
                        modifier = Modifier.weight(1.0f),
                        label = "Rotate Right",
                        active = uiState.rotationDirection == RotationDirection.RIGHT,
                        onPressChanged = { pressed ->
                            viewModel.setRotation(if (pressed) RotationDirection.RIGHT else null)
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Host",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Button(
                    onClick = viewModel::startHost,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.hostStartInFlight,
                ) {
                    Text(if (uiState.hostStartInFlight) "Starting host..." else "Start Host On Robot")
                }
                Text(
                    text = uiState.hostStartStatus ?: "Optional: start the Raspberry Pi host directly from the app over SSH.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.hostStartStatus == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            DebugConsoleCard(
                uiState = uiState,
                onToggleExpanded = { viewModel.setDebugConsoleExpanded(!uiState.debugConsoleExpanded) },
                onClear = viewModel::clearDebugLogs,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpeedMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.speedMode == mode,
                            onClick = { viewModel.setSpeedMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::stopMotion,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }

            Text(
                text = "This version controls only the base. You can either start the Raspberry Pi host from the app over SSH or run it manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = uiState.settings,
            sshSettings = uiState.sshSettings,
            onDismiss = { showSettings = false },
            onSave = { robotIp, commandPort, sshHost, sshPort, sshUser, sshPassword, hostStartCommand ->
                viewModel.saveSettings(
                    robotIp = robotIp,
                    commandPortText = commandPort,
                    sshHost = sshHost,
                    sshPortText = sshPort,
                    sshUser = sshUser,
                    sshPassword = sshPassword,
                    hostStartCommand = hostStartCommand,
                )
                showSettings = false
            },
        )
    }
}

@Composable
private fun StatusCard(uiState: AppUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Robot endpoint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = uiState.settings.endpoint,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "SSH host: ${uiState.sshSettings.sshHost}:${uiState.sshSettings.sshPort}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Speed: ${uiState.speedMode.label}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = uiState.lastError ?: "Sending drive commands every 75 ms",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.lastError == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun DebugConsoleCard(
    uiState: AppUiState,
    onToggleExpanded: () -> Unit,
    onClear: () -> Unit,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Text(
                        text = "Debug Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear debug log")
                    }
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (uiState.debugConsoleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand debug console",
                        )
                    }
                }
            }

            val visibleLogs = if (uiState.debugConsoleExpanded) {
                uiState.debugLogs
            } else {
                uiState.debugLogs.takeLast(6)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 120.dp, maxHeight = if (uiState.debugConsoleExpanded) 260.dp else 160.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (visibleLogs.isEmpty()) {
                        Text(
                            text = "No debug messages yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visibleLogs.forEach { entry ->
                            Text(
                                text = "[${entry.timestamp}] ${entry.message}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HoldButton(
    modifier: Modifier = Modifier,
    label: String,
    active: Boolean,
    onPressChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        onPressChanged(true)
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        onPressChanged(false)
                        true
                    }

                    else -> true
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: ConnectionSettings,
    sshSettings: com.lerobot.lekiwiremote.model.SshHostSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
) {
    var robotIp by remember(settings.robotIp) { mutableStateOf(settings.robotIp) }
    var commandPort by remember(settings.commandPort) { mutableStateOf(settings.commandPort.toString()) }
    var sshHost by remember(sshSettings.sshHost) { mutableStateOf(sshSettings.sshHost) }
    var sshPort by remember(sshSettings.sshPort) { mutableStateOf(sshSettings.sshPort.toString()) }
    var sshUser by remember(sshSettings.sshUser) { mutableStateOf(sshSettings.sshUser) }
    var sshPassword by remember(sshSettings.sshPassword) { mutableStateOf(sshSettings.sshPassword) }
    var hostStartCommand by remember(sshSettings.hostStartCommand) { mutableStateOf(sshSettings.hostStartCommand) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = robotIp,
                    onValueChange = { robotIp = it },
                    label = { Text("Robot IP") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = commandPort,
                    onValueChange = { commandPort = it.filter(Char::isDigit) },
                    label = { Text("Command port") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshHost,
                    onValueChange = { sshHost = it },
                    label = { Text("SSH host") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshPort,
                    onValueChange = { sshPort = it.filter(Char::isDigit) },
                    label = { Text("SSH port") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshUser,
                    onValueChange = { sshUser = it },
                    label = { Text("SSH user") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshPassword,
                    onValueChange = { sshPassword = it },
                    label = { Text("SSH password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = hostStartCommand,
                    onValueChange = { hostStartCommand = it },
                    label = { Text("Host start command") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Text(
                    text = "The app can optionally SSH into the Raspberry Pi and launch the host command for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(robotIp, commandPort, sshHost, sshPort, sshUser, sshPassword, hostStartCommand)
                },
                enabled = robotIp.isNotBlank() && commandPort.isNotBlank() && sshHost.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun JoystickPad(
    normalizedX: Float,
    normalizedY: Float,
    onValueChange: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1.0f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val normalized = normalizeJoystick(offset, size)
                        onValueChange(normalized.x, normalized.y)
                    },
                    onDragEnd = { onValueChange(0.0f, 0.0f) },
                    onDragCancel = { onValueChange(0.0f, 0.0f) },
                    onDrag = { change, _ ->
                        val normalized = normalizeJoystick(change.position, size)
                        onValueChange(normalized.x, normalized.y)
                        change.consume()
                    },
                )
            },
    ) {
        val knobColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val strokeColor = MaterialTheme.colorScheme.outline

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2.0f
            val joystickRadius = radius * 0.72f
            val knobRadius = radius * 0.22f
            val center = Offset(size.width / 2.0f, size.height / 2.0f)
            val knobCenter = Offset(
                x = center.x + normalizedX * joystickRadius,
                y = center.y + normalizedY * joystickRadius,
            )

            drawCircle(
                color = trackColor,
                radius = radius * 0.96f,
                center = center,
            )
            drawCircle(
                color = strokeColor,
                radius = joystickRadius,
                center = center,
                style = Stroke(width = 6.0f),
            )
            drawLine(
                color = strokeColor,
                start = Offset(center.x - joystickRadius, center.y),
                end = Offset(center.x + joystickRadius, center.y),
                strokeWidth = 4.0f,
            )
            drawLine(
                color = strokeColor,
                start = Offset(center.x, center.y - joystickRadius),
                end = Offset(center.x, center.y + joystickRadius),
                strokeWidth = 4.0f,
            )
            drawCircle(
                color = knobColor,
                radius = knobRadius,
                center = knobCenter,
            )
        }
    }
}

private fun normalizeJoystick(position: Offset, size: IntSize): Offset {
    val center = Offset(size.width / 2.0f, size.height / 2.0f)
    val dx = position.x - center.x
    val dy = position.y - center.y
    val maxDistance = min(size.width, size.height) * 0.36f

    val distance = Offset(dx, dy).getDistance()
    if (distance == 0.0f) {
        return Offset.Zero
    }

    val scale = min(1.0f, maxDistance / distance)
    return Offset(
        x = (dx * scale) / maxDistance,
        y = (dy * scale) / maxDistance,
    )
}
