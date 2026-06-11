package com.fenl.fenlzer.importing.local

import java.io.File
import java.security.MessageDigest

object Sha256 {
    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }

    fun hashBytes(bytes: ByteArray): String =
        bytesToHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return bytesToHex(digest.digest())
    }
}
