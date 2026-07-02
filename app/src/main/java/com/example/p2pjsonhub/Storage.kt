package com.example.p2pjsonhub

import android.content.Context
import java.io.File
import java.security.MessageDigest

object Storage {
    private const val DATA_DIR = "p2pdata"
    private val index = mutableSetOf<String>()

    fun init(context: Context) {
        val rootDir = File(context.filesDir, DATA_DIR)
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    fun save(json: String): String {
        val hash = sha256(json)
        return hash
    }

    fun get(hash: String): String? {
        return null
    }

    fun getAllHashes(): List<String> = index.toList()

    fun hasHash(hash: String): Boolean = index.contains(hash)

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
