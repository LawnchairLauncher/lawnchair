package ch.deletescape.lawnchair.widget;

import android.os.Process;
import android.os.UserHandle;

import java.text.Collator;
import java.util.Comparator;

import ch.deletescape.lawnchair.model.WidgetItem;

public class WidgetItemComparator implements Comparator<WidgetItem> {
    private final Collator mCollator = Collator.getInstance();
    private final UserHandle mMyUserHandle = Process.myUserHandle();

    @Override
    public int compare(WidgetItem widgetItem, WidgetItem widgetItem2) {
        int equals = (mMyUserHandle.equals(widgetItem.user) ? 1 : 0) ^ 1;
        if ((((mMyUserHandle.equals(widgetItem2.user) ? 1 : 0) ^ 1) ^ equals) != 0) {
            return equals != 0 ? 1 : -1;
        }
        equals = mCollator.compare(widgetItem.label, widgetItem2.label);
        if (equals != 0) {
            return equals;
        }
        equals = widgetItem.spanX * widgetItem.spanY;
        int i = widgetItem2.spanX * widgetItem2.spanY;
        if (equals == i) {
            equals = Integer.compare(widgetItem.spanY, widgetItem2.spanY);
        } else {
            equals = Integer.compare(equals, i);
        }
        return equals;
    }
}