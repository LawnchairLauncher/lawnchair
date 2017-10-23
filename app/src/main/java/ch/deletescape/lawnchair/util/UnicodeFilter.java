package ch.deletescape.lawnchair.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Attempts to replace any non-ascii characters to theirs ascii equivalents
 * by normalizing the character into Unicode NFD form and stripping out diacritic mark characters.
 */
public class UnicodeFilter {
    private static final Pattern DIACRITICS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static String filter(String source) {
        StringBuilder output = new StringBuilder(Normalizer.normalize(source, Normalizer.Form.NFD));

        // Special case characters that don't get stripped by the above technique.
        for (int i = 0; i < output.length(); i++) {
            if (output.charAt(i) == '\u0141') {
                output.deleteCharAt(i);
                output.insert(i, 'L');
            } else if (output.charAt(i) == '\u0142') {
                output.deleteCharAt(i);
                output.insert(i, 'l');
            }
        }

        // Note that ligatures will be left as is
        return DIACRITICS_PATTERN.matcher(output).replaceAll("");
    }
}
