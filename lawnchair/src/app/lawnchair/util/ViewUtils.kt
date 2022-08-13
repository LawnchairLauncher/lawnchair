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

fun OnAttachStateChangeListener(callback: (isAttached: Boolean) -> Unit) = object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View) = callback(true)
    override fun onViewDetachedFromWindow(v: View) = callback(false)
}

fun View.observeAttachedState(callback: (isAttached: Boolean) -> Unit): () -> Unit {
    var wasAttached = false
    val listener = OnAttachStateChangeListener { isAttached ->
        if (wasAttached != isAttached) {
            wasAttached = isAttached
            callback(isAttached)
        }
    }
    addOnAttachStateChangeListener(listener)
    if (isAttachedToWindow) {
        listener.onViewAttachedToWindow(this)
    }
    return { removeOnAttachStateChangeListener(listener) }
}
