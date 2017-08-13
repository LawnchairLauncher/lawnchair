package ch.deletescape.lawnchair.config

import android.content.Context
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.allapps.theme.AllAppsBaseTheme
import ch.deletescape.lawnchair.allapps.theme.AllAppsVerticalTheme
import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer

open class ThemerImpl : IThemer {

    var allAppsTheme: IAllAppsThemer? = null

    override fun allAppsTheme(context: Context): IAllAppsThemer {
        val useVerticalLayout = Utilities.getPrefs(context).verticalDrawerLayout
        if (allAppsTheme == null ||
                (useVerticalLayout && allAppsTheme !is AllAppsVerticalTheme) ||
                (!useVerticalLayout && allAppsTheme is AllAppsVerticalTheme))
            allAppsTheme = if (useVerticalLayout) AllAppsVerticalTheme(context) else AllAppsBaseTheme(context)
        return allAppsTheme!!
    }
}