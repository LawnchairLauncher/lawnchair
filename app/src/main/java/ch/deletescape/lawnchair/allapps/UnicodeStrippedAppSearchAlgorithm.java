package ch.deletescape.lawnchair.allapps;

import java.util.List;

import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.util.UnicodeFilter;

/**
 * A search algorithm that changes every non-ascii characters to theirs ascii equivalents and
 * then performs comparison.
 */
public class UnicodeStrippedAppSearchAlgorithm extends DefaultAppSearchAlgorithm {
    public UnicodeStrippedAppSearchAlgorithm(List<AppInfo> apps) {
        super(apps);
    }

    @Override
    protected boolean matches(AppInfo info, String query) {
        if (info.componentName.getPackageName().equals(LauncherAppState.getInstanceNoCreate().getContext().getPackageName()))
            return false;

        String title = UnicodeFilter.filter(info.title.toString().toLowerCase());
        String strippedQuery = UnicodeFilter.filter(query.trim());

        return super.matches(title, strippedQuery);
    }
}
