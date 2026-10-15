package app.lawnchair.fold

/** 0f = مطوي (الشاشة الخارجية)، 1f = مفتوح بالكامل. المحرك (E) هو المصدر الوحيد. */
interface FoldProgressListener {
    fun onFoldStart(opening: Boolean)
    fun onFoldProgress(progress: Float, opening: Boolean)
    fun onFoldEnd(opening: Boolean)
}
