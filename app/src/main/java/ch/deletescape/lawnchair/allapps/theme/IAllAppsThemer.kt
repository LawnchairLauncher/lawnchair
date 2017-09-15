package ch.deletescape.lawnchair.allapps.theme

interface IAllAppsThemer {

    val backgroundColor : Int
    val backgroundColorBlur : Int
    fun iconTextColor(backgroundAlpha: Int) : Int
    val iconTextLines : Int
    val searchTextColor : Int
    /*
     * this color is also used for the market search button, the market item divider and the search bar divider
     * currently ignored as hint color for round search bar
     */
    val searchBarHintTextColor : Int
    val fastScrollerHandleColor : Int
    val fastScrollerPopupTintColor: Int
    val fastScrollerPopupTextColor : Int

    val iconLayout: Int
    fun numIconPerRow(default: Int): Int
    fun iconHeight(default: Int): Int
}