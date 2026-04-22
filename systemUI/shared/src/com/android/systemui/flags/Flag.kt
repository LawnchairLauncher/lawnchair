/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.flags

import android.annotation.BoolRes
import android.annotation.IntegerRes
import android.annotation.StringRes
import android.os.Parcel
import android.os.Parcelable

/**
 * Base interface for flags that can change value on a running device.
 * @property teamfood Set to true to include this flag as part of the teamfood flag. This will
 *                    be removed soon.
 * @property name Used for server-side flagging where appropriate. Also used for display. No spaces.
 * @property namespace The server-side namespace that this flag lives under.
 */
interface Flag<T> {
    val teamfood: Boolean
    val name: String
    val namespace: String
}

interface ParcelableFlag<T> : Flag<T>, Parcelable {
    val default: T
    val overridden: Boolean
    override fun describeContents() = 0
}

interface ResourceFlag<T> : Flag<T> {
    val resourceId: Int
}

interface SysPropFlag<T> : Flag<T> {
    val default: T
}

/**
 * Base class for most common boolean flags.
 *
 * See [UnreleasedFlag] and [ReleasedFlag] for useful implementations.
 */
// Consider using the "parcelize" kotlin library.
abstract class BooleanFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Boolean = false,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<Boolean> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BooleanFlag> {
            override fun createFromParcel(parcel: Parcel) = object : BooleanFlag(parcel) {}
            override fun newArray(size: Int) = arrayOfNulls<BooleanFlag>(size)
        }
    }

    private constructor(
            id: Int,
            name: String,
            namespace: String,
            default: Boolean,
            teamfood: Boolean,
            overridden: Boolean,
            ) : this(name, namespace, default, teamfood, overridden)

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readBoolean(),
        teamfood = parcel.readBoolean(),
        overridden = parcel.readBoolean()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeBoolean(default)
        parcel.writeBoolean(teamfood)
        parcel.writeBoolean(overridden)
    }
}

/**
 * A Flag that is is false by default.
 *
 * It can be changed or overridden in debug builds but not in release builds.
 */
data class UnreleasedFlag constructor(
    override val name: String,
    override val namespace: String,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : BooleanFlag(name, namespace, false, teamfood, overridden)

/**
 * A Flag that is true by default.
 *
 * It can be changed or overridden in any build, meaning it can be turned off if needed.
 */
data class ReleasedFlag constructor(
    override val name: String,
    override val namespace: String,
    override val overridden: Boolean = false
) : BooleanFlag(name, namespace, true, teamfood = false, overridden)

/**
 * A Flag that reads its default values from a resource overlay instead of code.
 *
 * Prefer [UnreleasedFlag] and [ReleasedFlag].
 */
data class ResourceBooleanFlag constructor(
    override val name: String,
    override val namespace: String,
    @BoolRes override val resourceId: Int,
) : ResourceFlag<Boolean> {
    override val teamfood: Boolean = false
}

/**
 * A Flag that can reads its overrides from System Properties.
 *
 * This is generally useful for flags that come from or are used _outside_ of SystemUI.
 *
 * Prefer [UnreleasedFlag] and [ReleasedFlag].
 */
data class SysPropBooleanFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Boolean = false,
) : SysPropFlag<Boolean> {
    override val teamfood: Boolean = false
}

data class StringFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: String = "",
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<String> {
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<StringFlag> {
            override fun createFromParcel(parcel: Parcel) = StringFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<StringFlag>(size)
        }
    }

    private constructor(id: Int, name: String, namespace: String, default: String) : this(
            name,
            namespace,
            default
    )

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeString(default)
    }
}

data class ResourceStringFlag constructor(
    override val name: String,
    override val namespace: String,
    @StringRes override val resourceId: Int,
    override val teamfood: Boolean = false
) : ResourceFlag<String>

data class IntFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Int = 0,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<Int> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<IntFlag> {
            override fun createFromParcel(parcel: Parcel) = IntFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<IntFlag>(size)
        }
    }

    private constructor(id: Int, name: String, namespace: String, default: Int) : this(
            name,
            namespace,
            default
    )

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeInt(default)
    }
}

data class ResourceIntFlag constructor(
    override val name: String,
    override val namespace: String,
    @IntegerRes override val resourceId: Int,
    override val teamfood: Boolean = false
) : ResourceFlag<Int>

data class LongFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Long = 0,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<Long> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LongFlag> {
            override fun createFromParcel(parcel: Parcel) = LongFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<LongFlag>(size)
        }
    }

    private constructor(id: Int, name: String, namespace: String, default: Long) : this(
            name,
            namespace,
            default
    )

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeLong(default)
    }
}

data class FloatFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Float = 0f,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<Float> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<FloatFlag> {
            override fun createFromParcel(parcel: Parcel) = FloatFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<FloatFlag>(size)
        }
    }

    private constructor(id: Int, name: String, namespace: String, default: Float) : this(
            name,
            namespace,
            default
    )

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeFloat(default)
    }
}

data class ResourceFloatFlag constructor(
    override val name: String,
    override val namespace: String,
    override val resourceId: Int,
    override val teamfood: Boolean = false,
) : ResourceFlag<Int>

data class DoubleFlag constructor(
    override val name: String,
    override val namespace: String,
    override val default: Double = 0.0,
    override val teamfood: Boolean = false,
    override val overridden: Boolean = false
) : ParcelableFlag<Double> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DoubleFlag> {
            override fun createFromParcel(parcel: Parcel) = DoubleFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<DoubleFlag>(size)
        }
    }

    private constructor(id: Int, name: String, namespace: String, default: Double) : this(
            name,
            namespace,
            default
    )

    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        name = parcel.readString() ?: "",
        namespace = parcel.readString() ?: "",
        default = parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(0)
        parcel.writeString(name)
        parcel.writeString(namespace)
        parcel.writeDouble(default)
    }
}
