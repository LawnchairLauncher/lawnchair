@file:OptIn(ExperimentalSerializationApi::class)

package app.lawnchair.util

import android.content.Intent
import android.os.UserHandle
import androidx.core.os.UserHandleCompat
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
        return UserHandleCompat.getUserHandleForUid(decoder.decodeInt())
    }

    override fun serialize(encoder: Encoder, value: UserHandle) {
        return encoder.encodeInt(value.hashCode())
    }
}
