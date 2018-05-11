package ch.deletescape.lawnchair

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Parcelable
import android.os.UserHandle

interface EditableItemInfo : Parcelable {

    fun getTitle(): String
    fun getTitle(context: Context): String?
    fun setTitle(context: Context, title: String?)
    fun getIcon(context: Context): String?
    fun setIcon(context: Context, icon: String?)
    fun reloadIcon(launcher: Launcher)
    fun getIconBitmap(iconCache: IconCache): Bitmap

    val type: Int
    val user: UserHandle
    val componentName: ComponentName?
}