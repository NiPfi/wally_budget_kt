package net.loeu.wallybudget.data.snapshot

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class GzipSnapshotCodec {
    fun encodeToGzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(text)
        }
        return output.toByteArray()
    }

    fun decodeFromBytes(bytes: ByteArray): DecodedSnapshotPayload {
        return if (looksLikeGzip(bytes)) {
            DecodedSnapshotPayload(
                text = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() },
                compressed = true
            )
        } else {
            DecodedSnapshotPayload(
                text = bytes.toString(Charsets.UTF_8),
                compressed = false
            )
        }
    }

    private fun looksLikeGzip(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    }
}

data class DecodedSnapshotPayload(
    val text: String,
    val compressed: Boolean
)
