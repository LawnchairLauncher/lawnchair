package com.android.quickstep

interface SplitSelectionListener {
    /** Called when the first app has been selected with the intention to launch split screen */
    fun onSplitSelectionActive()

    /** Called when the second app has been selected with the intention to launch split screen */
    fun onSplitSelectionConfirmed()

    /**
     * Called when the user no longer is in the process of selecting apps for split screen.
     * [launchedSplit] will be true if selected apps have launched successfully (either in
     * split screen or fullscreen), false if the user canceled/exited the selection process
     */
    fun onSplitSelectionExit(launchedSplit: Boolean) {
    }
}