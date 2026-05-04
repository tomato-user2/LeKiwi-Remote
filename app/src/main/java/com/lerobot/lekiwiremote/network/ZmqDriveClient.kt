package com.lerobot.lekiwiremote.network

import com.lerobot.lekiwiremote.model.ConnectionSettings
import com.lerobot.lekiwiremote.model.DriveCommand
import org.zeromq.SocketType
import org.zeromq.ZContext

class ZmqDriveClient {
    private var context: ZContext? = null
    private var socket: org.zeromq.ZMQ.Socket? = null
    private var currentEndpoint: String? = null
    private var hasLoggedActiveEndpoint = false

    @Synchronized
    fun send(command: DriveCommand, settings: ConnectionSettings, log: (String) -> Unit = {}) {
        ensureSocket(settings, log)
        val activeSocket = socket ?: error("ZMQ socket is not initialized")
        if (!hasLoggedActiveEndpoint) {
            log("ZMQ ready on ${settings.endpoint}")
            hasLoggedActiveEndpoint = true
        }
        val sent = activeSocket.send(command.toJson())
        if (!sent) {
            error("Failed to send command to ${settings.endpoint}")
        }
    }

    @Synchronized
    fun close() {
        socket?.close()
        context?.close()
        socket = null
        context = null
        currentEndpoint = null
        hasLoggedActiveEndpoint = false
    }

    @Synchronized
    private fun ensureSocket(settings: ConnectionSettings, log: (String) -> Unit) {
        val endpoint = settings.endpoint
        if (endpoint == currentEndpoint && socket != null) {
            return
        }

        if (currentEndpoint != null && currentEndpoint != endpoint) {
            log("Switching ZMQ endpoint from $currentEndpoint to $endpoint")
        } else {
            log("Opening ZMQ connection to $endpoint")
        }
        close()

        context = ZContext(1)
        socket = context?.createSocket(SocketType.PUSH)?.apply {
            setConflate(true)
            setSendTimeOut(100)
            connect(endpoint)
        }
        currentEndpoint = endpoint
        hasLoggedActiveEndpoint = false
    }
}
