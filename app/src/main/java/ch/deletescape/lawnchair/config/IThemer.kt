package ch.deletescape.lawnchair.config

import android.content.Context
import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer
import ch.deletescape.lawnchair.popup.theme.IPopupThemer

interface IThemer {

    fun allAppsTheme(context: Context): IAllAppsThemer
    fun popupTheme(context: Context): IPopupThemer
}