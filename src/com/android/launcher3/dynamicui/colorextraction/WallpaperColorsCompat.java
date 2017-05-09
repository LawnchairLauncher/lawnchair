package com.android.launcher3.dynamicui.colorextraction;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.util.List;

/**
 * A wrapper around platform implementation of WallpaperColors until the
 * updated SDK is available.
 *
 * TODO remove this class if available by platform
 */
public class WallpaperColorsCompat implements Parcelable {

    private final Parcelable mObject;

    public WallpaperColorsCompat(Parcelable object) {
        mObject = object;
    }

    private Object invokeMethod(String methodName) {
        try {
            return mObject.getClass().getDeclaredMethod(methodName).invoke(mObject);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(mObject, i);
    }

    public static final Parcelable.Creator<WallpaperColorsCompat> CREATOR =
            new Parcelable.Creator<WallpaperColorsCompat>() {
                public WallpaperColorsCompat createFromParcel(Parcel source) {
                    Parcelable object = source.readParcelable(null);
                    return new WallpaperColorsCompat(object);
                }

                public WallpaperColorsCompat[] newArray(int size) {
                    return new WallpaperColorsCompat[size];
                }
            };

    public List<Pair<Color, Integer>> getColors() {
        try {
            return (List<Pair<Color, Integer>>) invokeMethod("getColors");
        } catch (Exception e) {
            return null;
        }
    }

    public boolean supportsDarkText() {
        try {
            return (Boolean) invokeMethod("supportsDarkText");
        } catch (Exception e) {
            return false;
        }
    }
}
