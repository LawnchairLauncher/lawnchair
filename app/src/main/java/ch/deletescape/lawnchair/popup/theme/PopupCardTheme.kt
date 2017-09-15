package ch.deletescape.lawnchair.popup.theme

import ch.deletescape.lawnchair.R

class PopupCardTheme : IPopupThemer {

    override val itemBg = R.drawable.card_round_rect
    override val itemSpacing = R.dimen.popup_items_spacing_card
    override val backgroundRadius = R.dimen.bg_card_rect_radius
    override val wrapInMain = true
}