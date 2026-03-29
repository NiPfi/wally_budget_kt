package net.loeu.wallybudget.data.local.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal object UserPreferencesSerializer : Serializer<UserPreferencesState> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override val defaultValue: UserPreferencesState = UserPreferencesState()

    override suspend fun readFrom(input: InputStream): UserPreferencesState {
        return try {
            json.decodeFromString(
                deserializer = UserPreferencesState.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Unable to read user preferences.", exception)
        }
    }

    override suspend fun writeTo(t: UserPreferencesState, output: OutputStream) {
        output.write(
            json.encodeToString(
                serializer = UserPreferencesState.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }
}
