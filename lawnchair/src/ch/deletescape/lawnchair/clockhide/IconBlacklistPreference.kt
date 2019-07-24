/*
 *     Copyright (c) 2017-2019 the Lawnchair team
 *     Copyright (c)  2019 oldosfan (would)
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.clockhide

import android.os.Parcel
import android.os.Parcelable

class IconBlacklistPreference(raw: String = "") : Parcelable {
    private var items: ArrayList<String> = ArrayList(raw.split(",").toList())
    val asString: String
        get() = run {
            items.joinToString(",")
        }

    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override fun toString(): String {
        return asString
    }

    fun add(item: String): IconBlacklistPreference {
        if (!items.contains(item)) {
            items.add(item)
        }
        return this
    }

    fun remove(item: String): IconBlacklistPreference {
        if (items.contains(item)) {
            items.remove(item)
        }
        return this
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(asString)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<IconBlacklistPreference> {
        override fun createFromParcel(parcel: Parcel): IconBlacklistPreference {
            return IconBlacklistPreference(parcel)
        }

        override fun newArray(size: Int): Array<IconBlacklistPreference?> {
            return arrayOfNulls(size)
        }
    }

}