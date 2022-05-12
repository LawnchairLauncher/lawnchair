package app.lawnchair.util

import android.view.View
import android.view.ViewGroup

fun ViewGroup.getAllChildren() = ArrayList<View>().also { getAllChildren(it) }

fun ViewGroup.getAllChildren(list: MutableList<View>) {
    for (i in (0 until childCount)) {
        val child = getChildAt(i)
        if (child is ViewGroup) {
            child.getAllChildren(list)
        } else {
            list.add(child)
        }
    }
}
