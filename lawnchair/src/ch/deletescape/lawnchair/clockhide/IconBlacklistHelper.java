/*
 *     Copyright (c) 2017-2019 the Lawnchair team
 *     Copyright (c)  2019 oldosfan (would)
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.clockhide;

import ch.deletescape.lawnchair.root.RootHelper;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;

public class IconBlacklistHelper {

    private IconBlacklistHelper() {

    }

    public static synchronized IconBlacklistPreference getCurrentPreference() throws IOException {
        Process process = Runtime.getRuntime()
                .exec(new String[]{"settings", "get", "secure", "icon_blacklist"});
        String output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
        return new IconBlacklistPreference(output);
    }

    public static synchronized void setCurrentPreference(IconBlacklistPreference preference)
            throws IOException {
        Runtime.getRuntime().exec(new String[]{"settings", "put", "secure", "icon_blacklist",
                preference.toString()});
    }
}
