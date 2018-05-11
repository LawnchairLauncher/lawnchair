package ch.deletescape.lawnchair.config

object ThemeProvider {

    // single instance for whole app
    private var themerIMPL: IThemer? = null

    fun init(flags: IThemer) {
        themerIMPL = flags
    }

    fun getThemer(): IThemer {
        if (themerIMPL == null)
            return ThemerImpl()
        return themerIMPL as IThemer
    }
}