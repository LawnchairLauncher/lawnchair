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

package ch.deletescape.lawnchair.smartspace.weathercom.models;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import org.apache.commons.lang3.SerializationException;

public abstract class BaseModel {

    public BaseModel() {
       try {
           //noinspection ResultOfMethodCallIgnored
           toString();
       } catch (Exception e) {
           throw new SerializationException("the class " + getClass().getName() + " is not serializable", e);
       }
    }

    @NonNull
    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
