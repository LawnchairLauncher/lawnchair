package ch.deletescape.lawnchair.util;

import java.text.Collator;
import java.util.Comparator;

public class LabelComparator implements Comparator<String> {
    private final Collator mCollator = Collator.getInstance();

    @Override
    public int compare(String str, String str2) {
        boolean isLetterOrDigit;
        boolean i = false;
        isLetterOrDigit = str.length() > 0 && Character.isLetterOrDigit(str.codePointAt(0));
        if (str2.length() > 0) {
            i = Character.isLetterOrDigit(str2.codePointAt(0));
        }
        if (isLetterOrDigit && !i) {
            return -1;
        }
        if (isLetterOrDigit || !i) {
            return this.mCollator.compare(str, str2);
        }
        return 1;
    }
}