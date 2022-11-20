package app.lawnchair.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.android.launcher3.R

fun copyToClipboard(
    context: Context,
    text: String,
    label: String = text,
    toastMessage: String? = context.getString(R.string.copied_toast),
) {
    val clipboardManager: ClipboardManager = context.requireSystemService()
    val clip = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clip)
    toastMessage?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
    }
}

fun getClipboardContent(
    context: Context,
): String? {
    val clipboardManager: ClipboardManager = context.requireSystemService()
    return clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
}
