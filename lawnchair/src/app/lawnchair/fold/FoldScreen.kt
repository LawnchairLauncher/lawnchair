package app.lawnchair.fold

/**
 * Which screen of a foldable a window is on. The single answer to "is this the large screen?",
 * computed once per `DeviceProperties`. [NONE] on phones and on tablets that don't fold.
 */
enum class FoldScreen { NONE, COVER, LARGE }
