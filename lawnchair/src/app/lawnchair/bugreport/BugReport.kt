package app.lawnchair.bugreport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import app.lawnchair.LawnchairApp
import com.android.launcher3.R
import java.io.File
import kotlinx.parcelize.Parcelize

@Parcelize
data class BugReport(val timestamp: Long, val id: Int, val type: String, val description: String, val contents: String,
                     val link: String?, val uploadError: Boolean = false, val file: File?) : Parcelable {

    constructor(id: Int, type: String, description: String, contents: String, file: File?) : this(
        System.currentTimeMillis(), id, type, description, contents, null, false, file)

    fun getTitle(context: Context): String = if (type == TYPE_UNCAUGHT_EXCEPTION) {
        context.getString(
            R.string.crash_report_notif_title,
            context.getString(R.string.derived_app_name),
        )
    } else type

    fun getFileUri(context: Context): Uri? = file?.let {
        LawnchairApp.getUriForFile(context, it)
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

    companion object {
        const val TYPE_UNCAUGHT_EXCEPTION = "Uncaught exception"
    }
}
