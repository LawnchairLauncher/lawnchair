package ch.deletescape.lawnchair.util;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Attempts to replace any non-ascii characters to theirs ascii equivalents
 */
public class UnicodeFilter {
    private static final Pattern DIACRITICS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}");

    public static String filter(String source) {
        StringBuilder output = new StringBuilder();
        final int sourceLength = source.length();

        for (int i = 0; i < sourceLength; i++) {
            String s = String.valueOf(source.charAt(i));

            // Try normalizing the character into Unicode NFKD form and
            // stripping out diacritic mark characters.
            s = Normalizer.normalize(s, Normalizer.Form.NFKD);
            s = DIACRITICS_PATTERN.matcher(s).replaceAll("");

            // Replace left over accents away using Apache's StringUtils
            s = StringUtils.stripAccents(s);
            output.append(s);
        }

        return output.toString();
    }
}
