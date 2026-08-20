package com.syncparty.core.audiotransfer

import java.io.File
import java.security.MessageDigest

/**
 * SHA-256 file hashing, used to (a) let a client tell the host "I already
 * have this file, skip transfer" and (b) verify integrity after transfer
 * (Section 9: "Calculate its hash... Verify checksum").
 */
object FileHasher {

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
