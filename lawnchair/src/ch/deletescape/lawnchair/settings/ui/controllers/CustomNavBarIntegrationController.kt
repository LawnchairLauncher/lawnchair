/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui.controllers

import android.content.Context
import android.support.annotation.Keep
import ch.deletescape.lawnchair.customnavbar.CustomNavBar
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.settings.ui.PreferenceController
import com.android.launcher3.Utilities

@Keep
class CustomNavBarIntegrationController(context: Context) : PreferenceController(context) {

    override val isVisible = Utilities.ATLEAST_NOUGAT
                             && !context.lawnchairApp.recentsEnabled
                             && CustomNavBar.getInstance(context).testVersionInstalled
}
