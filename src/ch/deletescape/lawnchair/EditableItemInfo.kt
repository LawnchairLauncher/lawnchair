package ch.deletescape.lawnchair

import android.content.Context

interface EditableItemInfo {

    fun getDefaultTitle(context: Context): String
    fun getTitle(context: Context): String?
    fun setTitle(context: Context, title: String?)

    var originalTitle: CharSequence?
}