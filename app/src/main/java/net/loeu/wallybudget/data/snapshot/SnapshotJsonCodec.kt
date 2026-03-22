package net.loeu.wallybudget.data.snapshot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1

class SnapshotJsonCodec(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    fun encode(snapshot: SnapshotEnvelopeV1): String = gson.toJson(snapshot)

    fun decode(input: String): SnapshotEnvelopeV1 {
        return try {
            val root = gson.fromJson(input, JsonObject::class.java)
            validateEnvelope(root)
            gson.fromJson(root, SnapshotEnvelopeV1::class.java)
        } catch (exception: JsonSyntaxException) {
            throw IllegalArgumentException("Malformed snapshot JSON", exception)
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException("Malformed snapshot JSON", exception)
        }
    }

    private fun validateEnvelope(root: JsonObject?) {
        requirePrimitive(root, "format")
        val schemaVersion = requireIntPrimitive(root, "schemaVersion")
        requirePrimitive(root, "snapshotId")
        requirePrimitive(root, "exportedAtEpochMs")
        requirePrimitive(root, "writerInstallId")
        requirePrimitive(root, "snapshotModClock")
        requirePrimitive(root, "appVersionName")
        requireObject(root, "settings")
        requireArray(root, "budgetPolicies")
        requireArray(root, "expenses")
        if (schemaVersion >= 2) {
            requireArray(root, "budgetAdjustments")
        }
        if (schemaVersion >= 3) {
            requireArray(root, "budgetBuckets")
            requireArray(root, "bucketAllocationPolicies")
            requireArray(root, "bucketAllocationAdjustments")
        }
        if (schemaVersion >= 5) {
            requireArray(root, "funds")
            requireArray(root, "fundTransactions")
        }
    }

    private fun requirePrimitive(root: JsonObject?, key: String) {
        val value = root?.get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive) {
            throw IllegalArgumentException("Missing required snapshot field: $key")
        }
    }

    private fun requireIntPrimitive(root: JsonObject?, key: String): Int {
        requirePrimitive(root, key)
        val value = checkNotNull(root?.get(key)) {
            "Missing required snapshot field: $key"
        }
        if (!value.asJsonPrimitive.isNumber) {
            throw IllegalArgumentException("Snapshot field '$key' must be numeric")
        }
        return value.asInt
    }

    private fun requireObject(root: JsonObject?, key: String) {
        val value = root?.get(key)
        if (value == null || value.isJsonNull || !value.isJsonObject) {
            throw IllegalArgumentException("Missing required snapshot object: $key")
        }
    }

    private fun requireArray(root: JsonObject?, key: String) {
        val value = root?.get(key)
        if (value == null || value.isJsonNull || !value.isJsonArray) {
            throw IllegalArgumentException("Missing required snapshot array: $key")
        }
    }
}
