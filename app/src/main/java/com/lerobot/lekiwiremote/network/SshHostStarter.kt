package com.lerobot.lekiwiremote.network

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.lerobot.lekiwiremote.model.SshHostSettings

class SshHostStarter {
    fun startHost(settings: SshHostSettings, log: (String) -> Unit = {}): String {
        require(settings.isConfigured) { "SSH settings are incomplete." }

        log("SSH connecting to ${settings.sshUser}@${settings.sshHost}:${settings.sshPort}")
        val session = JSch().getSession(settings.sshUser, settings.sshHost, settings.sshPort)
        session.setPassword(settings.sshPassword)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(5_000)
        log("SSH session connected")

        try {
            val channel = session.openChannel("exec") as ChannelExec
            log("Running remote host command")
            channel.setCommand(settings.hostStartCommand)
            channel.inputStream = null
            channel.setErrStream(null)
            channel.connect(5_000)
            log("Remote exec channel connected")

            try {
                while (!channel.isClosed) {
                    Thread.sleep(100)
                }
                val exitStatus = channel.exitStatus
                if (exitStatus != 0) {
                    error("Remote host start command exited with code $exitStatus")
                }
                log("Remote host command completed successfully")
            } finally {
                channel.disconnect()
                log("SSH exec channel closed")
            }
        } finally {
            session.disconnect()
            log("SSH session closed")
        }

        return "Host start command sent to ${settings.sshHost}"
    }
}
