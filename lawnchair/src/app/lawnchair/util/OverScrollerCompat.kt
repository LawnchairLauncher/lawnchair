package app.lawnchair.util

import android.widget.OverScroller
import com.android.launcher3.Utilities

object OverScrollerCompat {
    @JvmStatic
    fun setFinalX(scroller: OverScroller, newX: Int) {
        scroller.setFinalX(newX)
        if (!Utilities.ATLEAST_S) {
            val scrollerX: Any = getField(scroller, "mScrollerX")
            val mFinal: Int = getField(scrollerX, "mFinal")
            val mStart: Int = getField(scrollerX, "mStart")
            setField(scrollerX, "mSplineDistance", mFinal - mStart)
        }
    }

    @JvmStatic
    fun extendDuration(scroller: OverScroller, extend: Int) {
        scroller.extendDuration(extend)
        if (!Utilities.ATLEAST_S) {
            val scrollerX: Any = getField(scroller, "mScrollerX")
            val scrollerY: Any = getField(scroller, "mScrollerY")
            setField(scrollerX, "mSplineDuration", getField(scrollerX, "mDuration"))
            setField(scrollerY, "mSplineDuration", getField(scrollerY, "mDuration"))
        }
    }

    private inline fun <reified T : Any> getField(obj: Any, name: String): T {
        return try {
            val field = obj.javaClass.getDeclaredField(name)
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field[obj] as T
        } catch (e: NoSuchFieldException) {
            throw RuntimeException("Failed to get field: $name", e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to get field: $name", e)
        }
    }

    private fun setField(obj: Any, name: String, value: Any) {
        try {
            val field = obj.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field[obj] = value
        } catch (_: NoSuchFieldException) {
        } catch (_: IllegalAccessException) {
        }
    }
}
