package ch.deletescape.lawnchair.config

import android.content.Context
import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer

interface IThemer {

    // -------------------
    // 1) App Theme
    // -------------------

    // -------------------
    // 2) All Apps Drawer
    // -------------------

    fun allAppsTheme(context: Context): IAllAppsThemer
}