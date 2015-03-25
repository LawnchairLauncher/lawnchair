package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


class SectionedWidgetsRow {
    String section;
    List<List<Object>> widgets;

    public SectionedWidgetsRow(String sc) {
        section = sc;
    }
}

class SectionedWidgetsAlgorithm {
    public List<SectionedWidgetsRow> computeSectionedWidgetRows(List<Object> sortedWidgets,
            int widgetsPerRow) {
        List<SectionedWidgetsRow> rows = new ArrayList<>();
        LinkedHashMap<String, List<Object>> sections = computeSectionedApps(sortedWidgets);
        for (Map.Entry<String, List<Object>> sectionEntry : sections.entrySet()) {
            String section = sectionEntry.getKey();
            SectionedWidgetsRow row = new SectionedWidgetsRow(section);
            List<Object> widgets = sectionEntry.getValue();
            int numRows = (int) Math.ceil((float) widgets.size() / widgetsPerRow);
            for (int i = 0; i < numRows; i++) {
                List<Object> widgetsInRow = new ArrayList<>();
                int offset = i * widgetsPerRow;
                for (int j = 0; j < widgetsPerRow; j++) {
                    widgetsInRow.add(widgets.get(offset + j));
                }
                row.widgets.add(widgetsInRow);
            }
        }
        return rows;
    }

    private LinkedHashMap<String, List<Object>> computeSectionedApps(List<Object> sortedWidgets) {
        LinkedHashMap<String, List<Object>> sections = new LinkedHashMap<>();
        for (Object info : sortedWidgets) {
            String section = getSection(info);
            List<Object> sectionedWidgets = sections.get(section);
            if (sectionedWidgets == null) {
                sectionedWidgets = new ArrayList<>();
                sections.put(section, sectionedWidgets);
            }
            sectionedWidgets.add(info);
        }
        return sections;
    }

    private String getSection(Object widgetOrShortcut) {
        return "UNKNOWN";
    }
}

/**
 * The widgets list view container.
 */
public class WidgetsContainerView extends FrameLayout {


    public WidgetsContainerView(Context context) {
        this(context, null);
    }

    public WidgetsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
    }
}
