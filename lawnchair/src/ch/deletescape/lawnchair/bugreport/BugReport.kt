/*
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

package ch.deletescape.lawnchair.bugreport

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.content.FileProvider
import com.android.launcher3.BuildConfig

import com.android.launcher3.R
import java.io.File

data class BugReport(val id: Long, val type: String, val description: String, val contents: String?,
                     var link: String?, var uploadError: Boolean = false, val file: File?) : Parcelable {

    constructor(type: String, description: String, contents: String, file: File?) : this(
            System.currentTimeMillis(), type, description, contents, null, false, file)

    constructor(parcel: Parcel) : this(
            parcel.readLong(),
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString(),
            parcel.readString(),
            parcel.readByte() != 0.toByte(),
            parcel.readString()?.let { File(it) })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(type)
        parcel.writeString(description)
        parcel.writeString(contents)
        parcel.writeString(link)
        parcel.writeByte(if (uploadError) 1 else 0)
        parcel.writeString(file?.absolutePath)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun getTitle(context: Context): String {
        return if (type == TYPE_UNCAUGHT_EXCEPTION) {
            context.getString(R.string.crash_report_notif_title,
                              context.getString(R.string.derived_app_name))
        } else type
    }

    fun getFileUri(context: Context): Uri? {
        return file?.let {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", it)
        }
    }

    fun createShareIntent(context: Context): Intent {
        val sendIntent: Intent = Intent(Intent.ACTION_SEND).apply {
            val fileUri = getFileUri(context)
            if (fileUri != null) {
                putExtra(Intent.EXTRA_STREAM, fileUri)
            } else {
                putExtra(Intent.EXTRA_TEXT, link ?: contents)
            }
            type = "text/plain"
        }
        return Intent.createChooser(sendIntent, context.getText(R.string.lawnchair_bug_report))
    }

    val notificationId = id.toInt()

    companion object CREATOR : Parcelable.Creator<BugReport> {

        const val TYPE_UNCAUGHT_EXCEPTION = "Uncaught exception"
        const val TYPE_STRICT_MODE_VIOLATION = "Strict mode violation"

        override fun createFromParcel(parcel: Parcel): BugReport {
            return BugReport(parcel)
        }

        override fun newArray(size: Int): Array<BugReport?> {
            return arrayOfNulls(size)
        }
    }
}
