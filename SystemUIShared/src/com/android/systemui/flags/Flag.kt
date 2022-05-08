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

import android.os.Parcel
import android.os.Parcelable

interface Flag<T> : Parcelable {
    val id: Int
    val default: T
    val resourceOverride: Int

    override fun describeContents() = 0

    fun hasResourceOverride(): Boolean {
        return resourceOverride != -1
    }
}

// Consider using the "parcelize" kotlin library.

data class BooleanFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Boolean = false,
    override val resourceOverride: Int = -1
) : Flag<Boolean> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BooleanFlag> {
            override fun createFromParcel(parcel: Parcel) = BooleanFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<BooleanFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readBoolean()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeBoolean(default)
    }
}

data class StringFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: String = "",
    override val resourceOverride: Int = -1
) : Flag<String> {
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<StringFlag> {
            override fun createFromParcel(parcel: Parcel) = StringFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<StringFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(default)
    }
}

data class IntFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Int = 0,
    override val resourceOverride: Int = -1
) : Flag<Int> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<IntFlag> {
            override fun createFromParcel(parcel: Parcel) = IntFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<IntFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeInt(default)
    }
}

data class LongFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Long = 0,
    override val resourceOverride: Int = -1
) : Flag<Long> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LongFlag> {
            override fun createFromParcel(parcel: Parcel) = LongFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<LongFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeLong(default)
    }
}

data class FloatFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Float = 0f,
    override val resourceOverride: Int = -1
) : Flag<Float> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<FloatFlag> {
            override fun createFromParcel(parcel: Parcel) = FloatFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<FloatFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeFloat(default)
    }
}

data class DoubleFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Double = 0.0,
    override val resourceOverride: Int = -1
) : Flag<Double> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DoubleFlag> {
            override fun createFromParcel(parcel: Parcel) = DoubleFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<DoubleFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeDouble(default)
    }
}