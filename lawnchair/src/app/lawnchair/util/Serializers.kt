@file:OptIn(ExperimentalSerializationApi::class)

package app.lawnchair.util

import android.content.Intent
import android.os.Parcel
import android.os.UserHandle
import com.android.launcher3.util.ComponentKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(forClass = ComponentKey::class)
object ComponentKeySerializer : KSerializer<ComponentKey> {
    override val descriptor = PrimitiveSerialDescriptor("ComponentKey", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ComponentKey {
        return ComponentKey.fromString(decoder.decodeString())!!
    }

    override fun serialize(encoder: Encoder, value: ComponentKey) {
        encoder.encodeString(value.toString())
    }
}

@Serializer(forClass = Intent::class)
object IntentSerializer : KSerializer<Intent> {
    override val descriptor = PrimitiveSerialDescriptor("Intent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Intent {
        return Intent.parseUri(decoder.decodeString(), 0)
    }

    override fun serialize(encoder: Encoder, value: Intent) {
        return encoder.encodeString(value.toUri(0))
    }
}

@Serializer(forClass = UserHandle::class)
object UserHandlerSerializer : KSerializer<UserHandle> {
    override val descriptor = PrimitiveSerialDescriptor("UserHandle", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): UserHandle {
        // The stored value is a user id (see serialize), not an app uid, so restore the
        // handle from its parcel form; treating it as a uid would map user 10 to user 0.
        val parcel = Parcel.obtain()
        return try {
            parcel.writeInt(decoder.decodeInt())
            parcel.setDataPosition(0)
            UserHandle.readFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    override fun serialize(encoder: Encoder, value: UserHandle) {
        return encoder.encodeInt(value.hashCode())
    }
}
