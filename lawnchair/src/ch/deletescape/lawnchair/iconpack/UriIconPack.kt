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

package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.ItemInfo
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.ComponentKey
import java.io.FileDescriptor

class UriIconPack(context: Context) : IconPack(context, "lawnchairUriPack") {

    override val packInfo = IconPackList.DefaultPackInfo(context)
    override val entries = listOf<Entry>()

    private val entryCache = mutableMapOf<String, UriEntry>()

    override fun loadPack() {

    }

    override fun onDateChanged() {

    }

    override fun getEntryForComponent(key: ComponentKey): Entry? {
        throw NotImplementedError()
    }

    private fun getUriEntry(name: String?): UriEntry? {
        d("getUriEntry($name)")
        if (TextUtils.isEmpty(name)) return null
        val entry = entryCache.getOrPut(name!!) { UriEntry(context, Uri.parse(name)) }
        if (!entry.isAvailable) return null
        return entry
    }

    private fun getUriEntry(customIconEntry: IconPackManager.CustomIconEntry?): UriEntry? {
        return getUriEntry(customIconEntry?.icon)
    }

    override fun getIcon(name: String, iconDpi: Int): Drawable? {
        return getUriEntry(name)?.drawable ?: super.getIcon(name, iconDpi)
    }

    override fun getIcon(shortcutInfo: ShortcutInfoCompat, iconDpi: Int): Drawable? {
        throw NotImplementedError()
    }

    override fun getIcon(launcherActivityInfo: LauncherActivityInfo, iconDpi: Int,
                         flattenDrawable: Boolean, customIconEntry: IconPackManager.CustomIconEntry?,
                         iconProvider: LawnchairIconProvider?): Drawable? {
        return getUriEntry(customIconEntry)?.drawable
    }

    override fun newIcon(icon: Bitmap, itemInfo: ItemInfo,
                         customIconEntry: IconPackManager.CustomIconEntry?,
                         drawableFactory: LawnchairDrawableFactory): FastBitmapDrawable? {
        return getUriEntry(customIconEntry)?.bitmap?.let { FastBitmapDrawable(it) }
    }

    override fun supportsMasking() = false

    class UriEntry(private val context: Context, private val uri: Uri) : Entry() {

        override val identifierName = uri.toString()
        override val displayName = identifierName

        val bitmap by lazy { loadBitmap() }
        override val drawable get() = BitmapDrawable(context.resources, bitmap)

        override val isAvailable by lazy {
            try {
                bitmap
                true
            } catch (ignored: Throwable) {
                false
            }
        }

        private fun loadBitmap(): Bitmap {
            val parcelFileDescriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val fileDescriptor: FileDescriptor = parcelFileDescriptor.fileDescriptor
            val image: Bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image
        }

        override fun toCustomEntry(): IconPackManager.CustomIconEntry {
            return IconPackManager.CustomIconEntry("lawnchairUriPack", uri.toString())
        }
    }
}
