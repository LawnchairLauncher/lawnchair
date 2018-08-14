package ch.deletescape.lawnchair.touch

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import ch.deletescape.lawnchair.LawnchairLauncher

open class GestureTouchListener(context: Context) : View.OnTouchListener {

    private val gestureController = LawnchairLauncher.getLauncher(context).gestureController

    override fun onTouch(view: View?, ev: MotionEvent?): Boolean {
        return gestureController.onBlankAreaTouch(ev!!)
    }

    fun onLongPress() {
        gestureController.onLongPress()
    }

    fun setTouchDownPoint(touchDownPoint: PointF) {
        gestureController.touchDownPoint = touchDownPoint
    }
}
