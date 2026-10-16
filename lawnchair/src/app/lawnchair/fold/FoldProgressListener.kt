package app.lawnchair.fold

/** Fold progress: 0f folded (cover screen), 1f fully open, reported by FoldTransitionController. */
interface FoldProgressListener {
    fun onFoldStart(opening: Boolean)
    fun onFoldProgress(progress: Float, opening: Boolean)
    fun onFoldEnd(opening: Boolean)
}
