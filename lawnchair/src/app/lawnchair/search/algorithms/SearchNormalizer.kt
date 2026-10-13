package app.lawnchair.search.algorithms

import java.text.Normalizer
import java.util.Locale

private val NON_SPACING_MARKS = "\\p{Mn}+".toRegex()
private val FORMAT_CHARS = "\\p{Cf}+".toRegex()

/**
 * Normalizes app labels and search queries so that they can be compared loosely.
 *
 * - Folds compatibility characters (e.g. Arabic presentation forms and ligatures, full-width
 *   Latin) and removes non-spacing marks (accents, Arabic tashkeel, hamza above/below and madda,
 *   so أ إ آ ؤ ئ become ا ا ا و ي) through NFKD -> remove `\p{Mn}` -> NFC. Spacing marks are kept
 *   and composed text such as Hangul syllables is recomposed, so other scripts are not broken
 *   apart.
 * - Removes invisible format characters (`\p{Cf}`) such as bidi marks and zero-width joiners.
 * - Folds the remaining Arabic letter variants that people commonly type interchangeably,
 *   removes tatweel, and maps Arabic-Indic digits to ASCII digits.
 *
 * Both sides of a comparison must be normalized with the same [lowercase] value.
 *
 * @param lowercase Whether to lowercase the result. Pass `false` to keep the case for matchers
 * that rely on it, such as the camel case word breaks of [com.android.launcher3.search.StringMatcherUtility].
 */
fun normalizeForSearch(input: String, lowercase: Boolean = true): String {
    // Lowercase first, as lowercasing can add combining marks (e.g. Turkish dotted I).
    val text = if (lowercase) input.lowercase(Locale.getDefault()) else input
    val stripped = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(NON_SPACING_MARKS, "")
        .replace(FORMAT_CHARS, "")
    val composed = Normalizer.normalize(stripped, Normalizer.Form.NFC)
    // Hamza and madda forms (أ إ آ ؤ ئ) are already folded by removing the marks above.
    return buildString(composed.length) {
        for (c in composed) {
            when (c) {
                // Alef wasla -> alef
                '\u0671' -> append('\u0627')

                // Alef maksura -> yeh
                '\u0649' -> append('\u064A')

                // Teh marbuta -> heh
                '\u0629' -> append('\u0647')

                // Tatweel
                '\u0640' -> {}

                // Arabic-Indic digits
                in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))

                // Extended Arabic-Indic digits
                in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))

                else -> append(c)
            }
        }
    }
}

/**
 * Returns whether this string contains a character from the Arabic script.
 */
fun String.containsArabicScript(): Boolean = any {
    Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC
}
