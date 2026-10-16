package app.lawnchair.search.algorithms.engine.provider.apps

import java.text.Normalizer
import java.util.Locale

/** Unicode-aware matching, independent of the keyboard and device language. */
internal object SearchTextNormalizer {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT).replace(marks, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace("ـ", "")
        return spaces.replace(
            separators.replace(
                buildString(folded.length) {
                    folded.forEach { char ->
                        val digit = Character.digit(char, 10)
                        append(if (digit >= 0) '0' + digit else char)
                    }
                },
                " ",
            ),
            " ",
        ).trim()
    }
}
