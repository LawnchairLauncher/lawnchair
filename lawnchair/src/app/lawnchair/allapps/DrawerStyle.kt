package app.lawnchair.allapps

import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class DrawerStyle(
    @StringRes val nameResourceId: Int,
) {
    companion object {
        fun fromString(value: String): DrawerStyle = when (value) {
            "paged" -> PagedDrawer
            else -> OneDrawer
        }

        fun values() = listOf(OneDrawer, PagedDrawer)
    }
}

object OneDrawer : DrawerStyle(
    nameResourceId = R.string.drawer_style_one_drawer,
) {
    override fun toString() = "one"
}

object PagedDrawer : DrawerStyle(
    nameResourceId = R.string.drawer_style_paged_drawer,
) {
    override fun toString() = "paged"
}
