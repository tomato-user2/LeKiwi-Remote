package com.lerobot.lekiwiremote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lerobot.lekiwiremote.model.AppUiState
import com.lerobot.lekiwiremote.model.ConnectionSettings
import com.lerobot.lekiwiremote.model.DebugLogEntry
import com.lerobot.lekiwiremote.model.RotationDirection
import com.lerobot.lekiwiremote.model.SshHostSettings
import com.lerobot.lekiwiremote.model.SpeedMode
import com.lerobot.lekiwiremote.network.SshHostStarter
import com.lerobot.lekiwiremote.network.ZmqDriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = ZmqDriveClient()
    private val sshHostStarter = SshHostStarter()
    private var lastLoggedTransportError: String? = null

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        appendLog("App started")
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val snapshot = _uiState.value
                val errorMessage = runCatching {
                    client.send(snapshot.activeCommand, snapshot.settings, ::appendLog)
                }.exceptionOrNull()?.message

                _uiState.update { current ->
                    if (current.lastError == errorMessage) {
                        current
                    } else {
                        current.copy(lastError = errorMessage)
                    }
                }

                if (errorMessage != null && errorMessage != lastLoggedTransportError) {
                    appendLog("ZMQ error: $errorMessage")
                    lastLoggedTransportError = errorMessage
                } else if (errorMessage == null) {
                    lastLoggedTransportError = null
                }

                delay(COMMAND_PERIOD_MS)
            }
        }
    }

    fun updateJoystick(normalizedX: Float, normalizedY: Float) {
        _uiState.update {
            it.copy(
                joystickX = normalizedX.coerceIn(-1.0f, 1.0f),
                joystickY = normalizedY.coerceIn(-1.0f, 1.0f),
            )
        }
    }

    fun stopMotion() {
        _uiState.update {
            it.copy(
                joystickX = 0.0f,
                joystickY = 0.0f,
                rotationDirection = null,
            )
        }
    }

    fun setRotation(direction: RotationDirection?) {
        _uiState.update { it.copy(rotationDirection = direction) }
    }

    fun setSpeedMode(mode: SpeedMode) {
        appendLog("Speed mode set to ${mode.label}")
        _uiState.update { it.copy(speedMode = mode) }
    }

    fun setDebugConsoleExpanded(expanded: Boolean) {
        _uiState.update { it.copy(debugConsoleExpanded = expanded) }
    }

    fun clearDebugLogs() {
        _uiState.update { it.copy(debugLogs = emptyList()) }
        appendLog("Debug console cleared")
    }

    fun saveSettings(
        robotIp: String,
        commandPortText: String,
        sshHost: String,
        sshPortText: String,
        sshUser: String,
        sshPassword: String,
        hostStartCommand: String,
    ) {
        val cleanIp = robotIp.trim()
        val port = commandPortText.toIntOrNull() ?: DEFAULT_COMMAND_PORT
        val settings = ConnectionSettings(robotIp = cleanIp, commandPort = port)
        val sshSettings = SshHostSettings(
            sshHost = sshHost.trim().ifBlank { cleanIp },
            sshPort = sshPortText.toIntOrNull() ?: DEFAULT_SSH_PORT,
            sshUser = sshUser.trim(),
            sshPassword = sshPassword,
            hostStartCommand = hostStartCommand.trim(),
        )

        prefs.edit()
            .putString(KEY_ROBOT_IP, settings.robotIp)
            .putInt(KEY_COMMAND_PORT, settings.commandPort)
            .putString(KEY_SSH_HOST, sshSettings.sshHost)
            .putInt(KEY_SSH_PORT, sshSettings.sshPort)
            .putString(KEY_SSH_USER, sshSettings.sshUser)
            .putString(KEY_SSH_PASSWORD, sshSettings.sshPassword)
            .putString(KEY_HOST_START_COMMAND, sshSettings.hostStartCommand)
            .apply()

        appendLog("Saved connection settings for ${settings.endpoint} and SSH ${sshSettings.sshHost}:${sshSettings.sshPort}")
        _uiState.update { it.copy(settings = settings, sshSettings = sshSettings) }
    }

    fun startHost() {
        val snapshot = _uiState.value
        if (!snapshot.sshSettings.isConfigured) {
            appendLog("SSH start aborted: settings are incomplete")
            _uiState.update { it.copy(hostStartStatus = "SSH settings are incomplete.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(hostStartInFlight = true, hostStartStatus = null) }
            appendLog("Starting host over SSH")
            val status = runCatching {
                sshHostStarter.startHost(snapshot.sshSettings, ::appendLog)
            }.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Failed to start host over SSH." },
            )
            appendLog("SSH host start result: $status")
            _uiState.update { it.copy(hostStartInFlight = false, hostStartStatus = status) }
        }
    }

    override fun onCleared() {
        appendLog("Closing app resources")
        client.close()
        super.onCleared()
    }

    private fun loadInitialState(): AppUiState {
        val settings = ConnectionSettings(
            robotIp = prefs.getString(KEY_ROBOT_IP, DEFAULT_ROBOT_IP).orEmpty(),
            commandPort = prefs.getInt(KEY_COMMAND_PORT, DEFAULT_COMMAND_PORT),
        )
        val sshSettings = SshHostSettings(
            sshHost = prefs.getString(KEY_SSH_HOST, settings.robotIp).orEmpty(),
            sshPort = prefs.getInt(KEY_SSH_PORT, DEFAULT_SSH_PORT),
            sshUser = prefs.getString(KEY_SSH_USER, DEFAULT_SSH_USER).orEmpty(),
            sshPassword = prefs.getString(KEY_SSH_PASSWORD, "").orEmpty(),
            hostStartCommand = prefs.getString(KEY_HOST_START_COMMAND, DEFAULT_HOST_START_COMMAND).orEmpty(),
        )
        return AppUiState(
            settings = settings,
            sshSettings = sshSettings,
            debugLogs = listOf(
                DebugLogEntry.create("Loaded settings for ${settings.endpoint}"),
                DebugLogEntry.create("Loaded SSH target ${sshSettings.sshUser}@${sshSettings.sshHost}:${sshSettings.sshPort}"),
            ),
        )
    }

    private fun appendLog(message: String) {
        _uiState.update { current ->
            val updatedLogs = (current.debugLogs + DebugLogEntry.create(message)).takeLast(MAX_DEBUG_LOGS)
            current.copy(debugLogs = updatedLogs)
        }
    }

    companion object {
        private const val PREFS_NAME = "lekiwi_remote_prefs"
        private const val KEY_ROBOT_IP = "robot_ip"
        private const val KEY_COMMAND_PORT = "command_port"
        private const val KEY_SSH_HOST = "ssh_host"
        private const val KEY_SSH_PORT = "ssh_port"
        private const val KEY_SSH_USER = "ssh_user"
        private const val KEY_SSH_PASSWORD = "ssh_password"
        private const val KEY_HOST_START_COMMAND = "host_start_command"
        private const val DEFAULT_ROBOT_IP = "192.168.0.10"
        private const val DEFAULT_COMMAND_PORT = 5555
        private const val DEFAULT_SSH_PORT = 22
        private const val DEFAULT_SSH_USER = "pi"
        private const val DEFAULT_HOST_START_COMMAND =
            "nohup python -m lerobot.robots.lekiwi.lekiwi_host --robot.id=my_awesome_kiwi --host.connection_time_s=36000 >/tmp/lekiwi_host.log 2>&1 < /dev/null &"
        private const val COMMAND_PERIOD_MS = 75L
        private const val MAX_DEBUG_LOGS = 120
    }
}
