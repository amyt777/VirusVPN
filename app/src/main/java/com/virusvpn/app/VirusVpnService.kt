package com.virusvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Original VpnService for Virus VPN.
 * It only uses the open-source libv2ray core library (not any other VPN app).
 * No server is bundled: it reads the server the user added in the app.
 */
class VirusVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.virusvpn.app.START"
        const val ACTION_STOP = "com.virusvpn.app.STOP"
        const val BROADCAST_STATUS = "com.virusvpn.app.STATUS"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_ERROR = "error"
        const val EXTRA_DOWN = "down_bps"
        const val EXTRA_UP = "up_bps"
        const val EXTRA_START_TIME = "start_time"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var startTimeMillis: Long = 0L
            private set
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var controller: CoreController? = null
    private var coreThread: Thread? = null
    private var statsThread: Thread? = null
    @Volatile
    private var statsRunning = false

    private val handler = object : CoreCallbackHandler {
        override fun onEmitStatus(code: Long, message: String): Long = 0L
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCore()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startCore()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startCore() {
        if (isRunning) return
        val servers = ServerStore.getServers(this)
        val selectedId = ServerStore.getSelectedId(this)
        val server = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
        if (server == null) {
            sendStatus(false, "Add a server first")
            stopSelf()
            return
        }
        try {
            val builder = Builder()
            builder.setSession("Virus VPN")
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("1.1.1.1")
            val tun = builder.establish()
            if (tun == null) {
                sendStatus(false, "Could not create VPN interface")
                stopSelf()
                return
            }
            tunInterface = tun
            startForeground(1, buildNotification("Connected"))
            isRunning = true
            startTimeMillis = System.currentTimeMillis()
            ServerStore.setString(this, "connected_start", startTimeMillis.toString())
            sendStatus(true, null)
            startStats()
            val configJson = V2RayConfig.build(server, ServerStore.getTweaks(this))
            val tunFd = tun.fd
            coreThread = Thread {
                try {
                    Seq.setContext(applicationContext)
                    Libv2ray.initCoreEnv(filesDir.absolutePath, ServerStore.getDeviceId(this))
                    val c = Libv2ray.newCoreController(handler)
                    controller = c
                    c.startLoop(configJson, tunFd)
                } catch (e: Exception) {
                    Log.e("VirusVPN", "Core failed: ${e.message}")
                    sendStatus(false, e.message ?: "Core failed")
                }
            }
            coreThread?.start()
        } catch (e: Exception) {
            Log.e("VirusVPN", "Start failed: ${e.message}")
            sendStatus(false, e.message ?: "Start failed")
            stopSelf()
        }
    }

    /** Poll /proc/net/dev for tun interfaces: real bytes through the VPN, once a second. */
    private fun startStats() {
        statsRunning = true
        statsThread = Thread {
            var prevRx = -1L
            var prevTx = -1L
            while (statsRunning && isRunning) {
                try {
                    var rx = 0L
                    var tx = 0L
                    java.io.File("/proc/net/dev").readLines().forEach { line ->
                        val t = line.trim()
                        if (t.startsWith("tun")) {
                            val parts = t.split(":")[1].trim().split(Regex("\\s+"))
                            if (parts.size >= 9) {
                                rx += parts[0].toLongOrNull() ?: 0L
                                tx += parts[8].toLongOrNull() ?: 0L
                            }
                        }
                    }
                    if (prevRx >= 0) {
                        val down = (rx - prevRx).coerceAtLeast(0L)
                        val up = (tx - prevTx).coerceAtLeast(0L)
                        val intent = Intent(BROADCAST_STATUS)
                        intent.putExtra(EXTRA_RUNNING, true)
                        intent.putExtra(EXTRA_DOWN, down)
                        intent.putExtra(EXTRA_UP, up)
                        intent.putExtra(EXTRA_START_TIME, startTimeMillis)
                        sendBroadcast(intent)
                    }
                    prevRx = rx
                    prevTx = tx
                } catch (_: Exception) {
                }
                try { Thread.sleep(1000) } catch (_: Exception) { break }
            }
        }
        statsThread?.start()
    }

    private fun stopCore() {
        statsRunning = false
        try {
            controller?.stopLoop()
        } catch (_: Exception) {
        }
        controller = null
        try {
            tunInterface?.close()
        } catch (_: Exception) {
        }
        tunInterface = null
        isRunning = false
        startTimeMillis = 0L
        ServerStore.setString(this, "connected_start", "")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        sendStatus(false, null)
    }

    private fun sendStatus(running: Boolean, error: String?) {
        val intent = Intent(BROADCAST_STATUS)
        intent.putExtra(EXTRA_RUNNING, running)
        if (running) intent.putExtra(EXTRA_START_TIME, startTimeMillis)
        if (error != null) intent.putExtra(EXTRA_ERROR, error)
        sendBroadcast(intent)
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "virus_vpn"
        val channel = NotificationChannel(channelId, "Virus VPN", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("Virus VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
    }

    override fun onDestroy() {
        stopCore()
        super.onDestroy()
    }
}
