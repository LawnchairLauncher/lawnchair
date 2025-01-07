package app.lawnchair.util

fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag

fun Int.addFlag(flag: Int): Int = this or flag

fun Int.removeFlag(flag: Int): Int = this and flag.inv()

fun Int.toggleFlag(flag: Int): Int = if (hasFlag(flag)) removeFlag(flag) else addFlag(flag)

fun Int.setFlag(flag: Int, value: Boolean): Int = if (value) {
    addFlag(flag)
} else {
    removeFlag(flag)
}
