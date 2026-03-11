package net.loeu.wallybudget.data.snapshot

import java.security.MessageDigest

class SnapshotHasher {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }
}
