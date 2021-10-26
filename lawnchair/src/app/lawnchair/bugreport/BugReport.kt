package app.lawnchair.bugreport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.FileProvider
import com.android.launcher3.BuildConfig

import com.android.launcher3.R
import java.io.File

data class BugReport(val timestamp: Long, val id: Int, val type: String, val description: String, val contents: String,
                     var link: String?, var uploadError: Boolean = false, val file: File?) : Parcelable {

    constructor(id: Int, type: String, description: String, contents: String, file: File?) : this(
        System.currentTimeMillis(), id, type, description, contents, null, false, file)

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString(),
        parcel.readByte() != 0.toByte(),
        parcel.readString()?.let { File(it) })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(timestamp)
        parcel.writeInt(id)
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
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.bugreport.provider", it)
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

    val notificationId = id

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
