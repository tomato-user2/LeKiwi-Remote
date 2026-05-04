package com.lerobot.lekiwiremote.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ConnectionSettings(
    val robotIp: String = "192.168.0.10",
    val commandPort: Int = 5555,
) {
    val endpoint: String
        get() = "tcp://$robotIp:$commandPort"
}

data class SshHostSettings(
    val sshHost: String = "192.168.0.10",
    val sshPort: Int = 22,
    val sshUser: String = "pi",
    val sshPassword: String = "",
    val hostStartCommand: String =
        "nohup python -m lerobot.robots.lekiwi.lekiwi_host --robot.id=my_awesome_kiwi --host.connection_time_s=36000 >/tmp/lekiwi_host.log 2>&1 < /dev/null &",
) {
    val isConfigured: Boolean
        get() = sshHost.isNotBlank() && sshUser.isNotBlank() && sshPassword.isNotBlank() && hostStartCommand.isNotBlank()
}

enum class SpeedMode(
    val label: String,
    val linearSpeed: Float,
    val angularSpeed: Float,
) {
    SLOW("Slow", linearSpeed = 0.1f, angularSpeed = 30.0f),
    MEDIUM("Medium", linearSpeed = 0.2f, angularSpeed = 60.0f),
    FAST("Fast", linearSpeed = 0.3f, angularSpeed = 90.0f),
}

enum class RotationDirection {
    LEFT,
    RIGHT,
}

data class DriveCommand(
    val x: Double,
    val y: Double,
    val theta: Double,
) {
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"x.vel\":")
            append(x)
            append(",")
            append("\"y.vel\":")
            append(y)
            append(",")
            append("\"theta.vel\":")
            append(theta)
            append("}")
        }
    }
}

data class DebugLogEntry(
    val timestamp: String,
    val message: String,
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun create(message: String): DebugLogEntry {
            return DebugLogEntry(
                timestamp = LocalTime.now().format(formatter),
                message = message,
            )
        }
    }
}

data class AppUiState(
    val settings: ConnectionSettings = ConnectionSettings(),
    val sshSettings: SshHostSettings = SshHostSettings(),
    val speedMode: SpeedMode = SpeedMode.MEDIUM,
    val joystickX: Float = 0.0f,
    val joystickY: Float = 0.0f,
    val rotationDirection: RotationDirection? = null,
    val lastError: String? = null,
    val hostStartStatus: String? = null,
    val hostStartInFlight: Boolean = false,
    val debugLogs: List<DebugLogEntry> = emptyList(),
    val debugConsoleExpanded: Boolean = false,
) {
    val activeCommand: DriveCommand
        get() {
            val xVelocity = -joystickY * speedMode.linearSpeed
            val yVelocity = -joystickX * speedMode.linearSpeed
            val thetaVelocity = when (rotationDirection) {
                RotationDirection.LEFT -> speedMode.angularSpeed
                RotationDirection.RIGHT -> -speedMode.angularSpeed
                null -> 0.0f
            }

            return DriveCommand(
                x = xVelocity.toDouble(),
                y = yVelocity.toDouble(),
                theta = thetaVelocity.toDouble(),
            )
        }
}
