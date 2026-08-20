package com.syncparty.core.networking

import com.syncparty.core.common.SyncMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Simple length-prefixed JSON framing over a TCP socket:
 *   [4 bytes big-endian length][UTF-8 JSON payload]
 *
 * Control-plane messages (play/pause/seek/clock-sync/etc.) are small and
 * infrequent enough that JSON is fine here (Section 24: reliable delivery
 * matters more than raw throughput for these). Bulk file bytes for track
 * transfer use a SEPARATE binary framing — see FileChunkFraming in
 * core:audiotransfer — specifically because Section 10 warns against
 * shipping large files as base64/JSON.
 */
object WireFormat {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun writeMessage(out: DataOutputStream, message: SyncMessage) {
        val payload = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        out.writeInt(payload.size)
        out.write(payload)
        out.flush()
    }

    /** Returns null on clean stream end. Throws on malformed/truncated frames. */
    fun readMessage(input: DataInputStream): SyncMessage? {
        val length = try {
            input.readInt()
        } catch (e: EOFException) {
            return null
        }
        require(length in 0..(16 * 1024 * 1024)) { "Refusing oversized frame: $length bytes" }
        val buffer = ByteArray(length)
        input.readFully(buffer)
        return json.decodeFromString(SyncMessage.serializer(), String(buffer, Charsets.UTF_8))
    }
}
