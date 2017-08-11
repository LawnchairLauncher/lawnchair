package ch.deletescape.lawnchair.preferences

import android.content.Context

interface IThemer {

    // -------------------
    // 1) App Theme
    // -------------------

    // -------------------
    // 2) All Apps Drawer
    // -------------------

    fun allAppsBackgroundColor(context: Context) : Int
    fun allAppsBackgroundColorBlur(context: Context) : Int
}