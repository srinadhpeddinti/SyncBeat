package com.syncparty.core.audiotransfer

import com.syncparty.core.common.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/**
 * Binary framing for bulk file transfer — deliberately NOT the JSON
 * SyncMessage channel (Section 10: "Do not send extremely large files
 * through inefficient JSON/base64 messages... Use binary transfer").
 *
 * Runs on its own TCP port, separate from the control-plane port used by
 * TcpHostTransport, so large file transfers never head-of-line-block
 * playback commands.
 *
 * Wire format per request, sent by the CLIENT after it decides (via
 * TrackInfo message on the control channel) that it needs the file:
 *
 *   [8 bytes] resume offset (0 for a fresh download)
 *   [UTF-8 length-prefixed trackId string]
 *
 * Server responds:
 *   [8 bytes] total file size
 *   [raw bytes from resume offset to end]
 */
class FileTransferServer(private val mediaDirectory: File) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** In-memory index of trackId -> local file path on the HOST, populated as tracks are added. */
    private val trackFiles = mutableMapOf<String, File>()

    fun registerTrack(track: Track, file: File) {
        trackFiles[track.id] = file
    }

    suspend fun start(): Int = withContext(Dispatchers.IO) {
        val socket = ServerSocket(0)
        serverSocket = socket
        scope.launch {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                scope.launch { serveOne(client) }
            }
        }
        socket.localPort
    }

    private fun serveOne(socket: Socket) {
        socket.use {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val resumeOffset = input.readLong()
            val trackIdLength = input.readInt()
            val trackIdBytes = ByteArray(trackIdLength)
            input.readFully(trackIdBytes)
            val trackId = String(trackIdBytes, Charsets.UTF_8)

            val file = trackFiles[trackId] ?: run {
                output.writeLong(-1) // signal "not found"
                return
            }

            output.writeLong(file.length())

            file.inputStream().use { fileIn ->
                if (resumeOffset > 0) fileIn.skip(resumeOffset)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = fileIn.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
            output.flush()
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}

/** Client side: connects to the host's file transfer port and downloads/resumes a track. */
class FileTransferClient(private val mediaDirectory: File) {

    /**
     * Downloads (or resumes) [trackId] from [hostAddress]:[filePort] into
     * [mediaDirectory], reporting progress via [onProgress] (0f..1f).
     * Returns the downloaded file, or null on failure.
     */
    suspend fun downloadTrack(
        trackId: String,
        expectedSha256: String,
        expectedFileName: String,
        hostAddress: String,
        filePort: Int,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val destFile = File(mediaDirectory, sanitizeFileName(expectedFileName))
        val resumeOffset = if (destFile.exists()) destFile.length() else 0L

        // Already fully present and verified? Skip transfer entirely (Section 9 step 3/4).
        if (destFile.exists() && FileHasher.sha256(destFile) == expectedSha256) {
            onProgress(1f)
            return@withContext destFile
        }

        val socket = Socket(hostAddress, filePort)
        socket.use {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            output.writeLong(resumeOffset)
            val idBytes = trackId.toByteArray(Charsets.UTF_8)
            output.writeInt(idBytes.size)
            output.write(idBytes)
            output.flush()

            val totalSize = input.readLong()
            if (totalSize < 0) return@withContext null // host reported "not found"

            val appendMode = resumeOffset > 0
            destFile.outputStream().let { rawOut ->
                if (!appendMode) rawOut else java.io.FileOutputStream(destFile, true)
            }.use { fileOut ->
                val buffer = ByteArray(256 * 1024)
                var received = resumeOffset
                val grandTotal = resumeOffset + totalSize
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    fileOut.write(buffer, 0, read)
                    received += read
                    if (grandTotal > 0) onProgress(received.toFloat() / grandTotal.toFloat())
                }
            }
        }

        // Verify checksum (Section 9 step 5, Section 39: validate file hashes).
        val actualHash = FileHasher.sha256(destFile)
        if (actualHash != expectedSha256) {
            destFile.delete()
            return@withContext null
        }

        destFile
    }

    /** Prevents path traversal — Section 39: "prevent arbitrary file paths." */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(200)
    }
}
