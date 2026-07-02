package com.example.p2pjsonhub

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class HttpServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning) e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0]
            val path = parts[1]

            var line: String?
            var contentLength = 0
            while (true) {
                line = reader.readLine()
                if (line.isNullOrEmpty()) break
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val writer = PrintWriter(client.getOutputStream(), true)

            when {
                method == "POST" && path == "/api/json" -> {
                    val bodyChars = CharArray(contentLength)
                    reader.read(bodyChars, 0, contentLength)
                    val json = String(bodyChars)
                    val hash = Storage.save(json)
                    writer.println("HTTP/1.1 200 OK")
                    writer.println("Content-Type: application/json")
                    writer.println()
                    writer.println("{\"status\":\"ok\",\"hash\":\"$hash\"}")
                }

                method == "GET" && path.startsWith("/api/json/") -> {
                    val hash = path.substringAfter("/api/json/")
                    val data = Storage.get(hash)
                    if (data != null) {
                        writer.println("HTTP/1.1 200 OK")
                        writer.println("Content-Type: application/json")
                        writer.println("Content-Length: ${data.length}")
                        writer.println()
                        writer.println(data)
                    } else {
                        writer.println("HTTP/1.1 404 Not Found")
                        writer.println()
                        writer.println("Not found")
                    }
                }

                else -> {
                    writer.println("HTTP/1.1 404 Not Found")
                    writer.println()
                    writer.println("Not found")
                }
            }

            writer.flush()
            client.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try { client.close() } catch (_: Exception) {}
        }
    }
}
