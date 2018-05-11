package ch.deletescape.lawnchair.popup.theme

import ch.deletescape.lawnchair.R

class PopupBaseTheme : IPopupThemer {

    override val itemBg = R.drawable.bg_white_round_rect
    override val childItemBg = R.drawable.bg_white_round_rect
    override val itemSpacing = R.dimen.popup_items_spacing
    override val backgroundRadius = R.dimen.bg_round_rect_radius
    override val wrapInMain = false
}