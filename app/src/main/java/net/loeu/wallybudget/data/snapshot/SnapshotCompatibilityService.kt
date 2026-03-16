package net.loeu.wallybudget.data.snapshot

import net.loeu.wallybudget.domain.model.SnapshotError

class SnapshotCompatibilityService {
    fun validateSchemaVersion(schemaVersion: Int): SnapshotError? {
        return if (schemaVersion in SUPPORTED_SCHEMA_VERSIONS) {
            null
        } else {
            SnapshotError.UnsupportedSchemaVersion
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        val SUPPORTED_SCHEMA_VERSIONS = 1..2
        const val SNAPSHOT_FORMAT = "wallybudget-snapshot"
    }
}
