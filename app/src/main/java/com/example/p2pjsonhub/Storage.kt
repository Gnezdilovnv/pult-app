package com.example.p2pjsonhub

import android.content.Context
import java.io.File
import java.security.MessageDigest

object Storage {
    private const val DATA_DIR = "p2pdata"
    private const val INDEX_FILE = "index.txt"
    private lateinit var rootDir: File
    private val index = mutableSetOf<String>()

    fun init(context: Context) {
        rootDir = File(context.filesDir, DATA_DIR)
        if (!rootDir.exists()) rootDir.mkdirs()
        loadIndex()
    }

    private fun loadIndex() {
        val idxFile = File(rootDir, INDEX_FILE)
        if (idxFile.exists()) {
            idxFile.readLines().forEach { index.add(it.trim()) }
        }
    }

    private fun saveIndex() {
        File(rootDir, INDEX_FILE).writeText(index.joinToString("\n"))
    }

    fun save(json: String): String {
        val hash = sha256(json)
        val file = File(rootDir, hash)
        if (!file.exists()) {
            file.writeText(json)
            index.add(hash)
            saveIndex()
        }
        return hash
    }

    fun get(hash: String): String? {
        val file = File(rootDir, hash)
        return if (file.exists()) file.readText() else null
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
