package net.loeu.wallybudget.data.snapshot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.loeu.wallybudget.data.snapshot.model.SnapshotEnvelopeV1

class SnapshotJsonCodec(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    fun encode(snapshot: SnapshotEnvelopeV1): String = gson.toJson(snapshot)

    fun decode(input: String): SnapshotEnvelopeV1 {
        return try {
            gson.fromJson(input, SnapshotEnvelopeV1::class.java)
        } catch (exception: JsonSyntaxException) {
            throw IllegalArgumentException("Malformed snapshot JSON", exception)
        }
    }
}
