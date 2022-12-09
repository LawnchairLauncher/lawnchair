/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.deviceemulator.models;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT_LANDSCAPE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE;
import static com.android.launcher3.testing.shared.ResourceUtils.getDimenByName;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.util.ArrayMap;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

public class DeviceEmulationData {

    public final int width;
    public final int height;
    public final int density;
    public final String name;
    public final String[] grids;
    public final Rect cutout;
    public final Map<String, Integer> resourceOverrides;

    private static final String[] EMULATED_SYSTEM_RESOURCES = new String[]{
            NAVBAR_HEIGHT,
            NAVBAR_HEIGHT_LANDSCAPE,
            NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE,
            "status_bar_height",
    };

    public DeviceEmulationData(int width, int height, int density, Rect cutout, String name,
            String[] grid,
            Map<String, Integer> resourceOverrides) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.name = name;
        this.grids = grid;
        this.cutout = cutout;
        this.resourceOverrides = resourceOverrides;
    }

    public static DeviceEmulationData deviceFromJSON(JSONObject json) throws JSONException {
        int width = json.getInt("width");
        int height = json.getInt("height");
        int density = json.getInt("density");
        String name = json.getString("name");

        JSONArray gridArray = json.getJSONArray("grids");
        String[] grids = new String[gridArray.length()];
        for (int i = 0, count = grids.length; i < count; i++) {
            grids[i] = gridArray.getString(i);
        }

        IntArray deviceCutout = IntArray.fromConcatString(json.getString("cutout"));
        Rect cutout = new Rect(deviceCutout.get(0), deviceCutout.get(1), deviceCutout.get(2),
                deviceCutout.get(3));


        JSONObject resourceOverridesJson = json.getJSONObject("resourceOverrides");
        Map<String, Integer> resourceOverrides = new ArrayMap<>();
        for (String key : resourceOverridesJson.keySet()) {
            resourceOverrides.put(key, resourceOverridesJson.getInt(key));
        }
        return new DeviceEmulationData(width, height, density, cutout, name, grids,
                resourceOverrides);
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        try {
            json.put("width", width);
            json.put("height", height);
            json.put("density", density);
            json.put("name", name);
            json.put("cutout", IntArray.wrap(
                    cutout.left, cutout.top, cutout.right, cutout.bottom).toConcatString());

            JSONArray gridArray = new JSONArray();
            Arrays.stream(grids).forEach(gridArray::put);
            json.put("grids", gridArray);


            JSONObject resourceOverrides = new JSONObject();
            for (Map.Entry<String, Integer> e : this.resourceOverrides.entrySet()) {
                resourceOverrides.put(e.getKey(), e.getValue());
            }
            json.put("resourceOverrides", resourceOverrides);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    public static DeviceEmulationData getCurrentDeviceData(Context context) {
        DisplayController.Info info = DisplayController.INSTANCE.get(context).getInfo();
        String[] grids = InvariantDeviceProfile.INSTANCE.get(context)
                .parseAllGridOptions(context).stream()
                .map(go -> go.name).toArray(String[]::new);
        String code = Build.MODEL.replaceAll("\\s", "").toLowerCase();

        Map<String, Integer> resourceOverrides = new ArrayMap<>();
        for (String s : EMULATED_SYSTEM_RESOURCES) {
            resourceOverrides.put(s, getDimenByName(s, context.getResources(), 0));
        }
        return new DeviceEmulationData(info.currentSize.x, info.currentSize.y,
                info.getDensityDpi(), info.cutout, code, grids, resourceOverrides);
    }

    public static DeviceEmulationData getDevice(String deviceCode) throws Exception {
        return DeviceEmulationData.deviceFromJSON(readJSON().getJSONObject(deviceCode));
    }

    private static JSONObject readJSON() throws Exception {
        Context context = getInstrumentation().getContext();
        Resources myRes = context.getResources();
        int resId = myRes.getIdentifier("devices", "raw", context.getPackageName());
        try (InputStream is = myRes.openRawResource(resId)) {
            return new JSONObject(new String(IOUtils.toByteArray(is)));
        }
    }

}
