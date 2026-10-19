@file:JvmName("FolderUtils")
package app.lawnchair.util

import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.FolderInfo

/**
 * Sorts the contents of a folder alphabetically by title.
 */
fun FolderInfo.sortAlphabetically(modelWriter: ModelWriter? = null) {
    val items = getContents()


    items.sortWith(Comparator { a, b ->
        val titleA = a.title?.toString() ?: ""
        val titleB = b.title?.toString() ?: ""
        titleA.compareTo(titleB, ignoreCase = true)
    })

    items.forEachIndexed { index, item ->
        item.rank = index
    }


    modelWriter?.let { writer ->
        items.forEach { item ->
            writer.updateItemInDatabase(item)
        }
    }
}
