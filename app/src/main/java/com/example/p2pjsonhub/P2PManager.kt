package com.example.p2pjsonhub

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets

class P2PManager(private val context: Context) {
    companion object {
        private const val MULTICAST_GROUP = "239.255.255.250"
        private const val MULTICAST_PORT = 9999
        private const val TCP_PORT = 9998
        private const val HTTP_PORT = 8080
        private val TAG = "P2PManager"
    }

    private var isRunning = false
    private var multicastSocket: MulticastSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private val knownPeers = mutableSetOf<String>()

    fun start() {
        if (isRunning) return
        isRunning = true

        Thread { udpListener() }.start()
        Thread { udpSender() }.start()
        Thread { tcpServer() }.start()
    }

    fun stop() {
        isRunning = false
        try { multicastSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        knownPeers.clear()
    }

    private fun udpListener() {
        try {
            multicastSocket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                joinGroup(InetAddress.getByName(MULTICAST_GROUP))
            }
            val buffer = ByteArray(1024)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    handleUdpMessage(msg, packet.address)
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun udpSender() {
        val group = InetAddress.getByName(MULTICAST_GROUP)
        val socket = DatagramSocket()
        while (isRunning) {
            try {
                val localIp = getLocalIpAddress()
                val model = Build.MODEL
                val msg = "PING|$localIp|$HTTP_PORT|$model"
                val data = msg.toByteArray(StandardCharsets.UTF_8)
                val packet = DatagramPacket(data, data.size, group, MULTICAST_PORT)
                socket.send(packet)
            } catch (_: Exception) { }
            Thread.sleep(30000)
        }
        socket.close()
    }

    private fun handleUdpMessage(msg: String, sender: InetAddress) {
        val parts = msg.split("|")
        if (parts.size < 4) return
        val type = parts[0]
        val ip = parts[1]
        val port = parts[2].toIntOrNull() ?: HTTP_PORT
        val model = parts[3]

        if (sender.hostAddress == getLocalIpAddress()) return

        when (type) {
            "PING" -> {
                try {
                    val response = "PONG|${getLocalIpAddress()}|$HTTP_PORT|${Build.MODEL}"
                    val data = response.toByteArray(StandardCharsets.UTF_8)
                    val socket = DatagramSocket()
                    val packet = DatagramPacket(data, data.size, sender, MULTICAST_PORT)
                    socket.send(packet)
                    socket.close()
                } catch (_: Exception) {}
                syncWithPeer(ip, port)
            }
            "PONG" -> {
                knownPeers.add("$ip:$port")
                syncWithPeer(ip, port)
            }
        }
    }

    private fun syncWithPeer(ip: String, httpPort: Int) {
        try {
            val socket = Socket(ip, TCP_PORT)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))

            val myHashes = Storage.getAllHashes()
            val myList = myHashes.joinToString(",")
            writer.println("HASHES|$myList")

            val response = reader.readLine() ?: return
            if (!response.startsWith("HASHES|")) return
            val theirHashes = response.substringAfter("HASHES|").split(",").filter { it.isNotEmpty() }

            val missing = theirHashes.filter { !Storage.hasHash(it) }
            if (missing.isNotEmpty()) {
                for (hash in missing) {
                    try {
                        val httpUrl = "http://$ip:$httpPort/api/json/$hash"
                        val url = URL(httpUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 5000
                        val input = conn.inputStream.bufferedReader(StandardCharsets.UTF_8)
                        val json = input.readText()
                        Storage.save(json)
                    } catch (_: Exception) {}
                }
            }

            socket.close()
        } catch (_: Exception) {}
    }

    private fun tcpServer() {
        try {
            tcpServerSocket = ServerSocket(TCP_PORT)
            while (isRunning) {
                val client = tcpServerSocket?.accept() ?: break
                Thread { handleTcpClient(client) }.start()
            }
        } catch (_: Exception) {}
    }

    private fun handleTcpClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val line = reader.readLine() ?: return
            if (!line.startsWith("HASHES|")) return
            val theirHashes = line.substringAfter("HASHES|").split(",").filter { it.isNotEmpty() }

            val myHashes = Storage.getAllHashes()
            val myList = myHashes.joinToString(",")
            writer.println("HASHES|$myList")

            socket.close()
        } catch (_: Exception) {}
    }

    private fun getLocalIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo?.ipAddress ?: return "127.0.0.1"
        return String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
    }
}
