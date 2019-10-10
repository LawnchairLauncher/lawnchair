/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.shared.plugins;

import android.util.ArrayMap;

import com.android.systemui.plugins.annotations.Dependencies;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.annotations.Requirements;
import com.android.systemui.plugins.annotations.Requires;

import java.util.function.BiConsumer;

public class VersionInfo {

    private final ArrayMap<Class<?>, Version> mVersions = new ArrayMap<>();
    private Class<?> mDefault;

    public boolean hasVersionInfo() {
        return !mVersions.isEmpty();
    }

    public int getDefaultVersion() {
        return mVersions.get(mDefault).mVersion;
    }

    public VersionInfo addClass(Class<?> cls) {
        if (mDefault == null) {
            // The legacy default version is from the first class we add.
            mDefault = cls;
        }
        addClass(cls, false);
        return this;
    }

    private void addClass(Class<?> cls, boolean required) {
        if (mVersions.containsKey(cls)) return;
        ProvidesInterface provider = cls.getDeclaredAnnotation(ProvidesInterface.class);
        if (provider != null) {
            mVersions.put(cls, new Version(provider.version(), true));
        }
        Requires requires = cls.getDeclaredAnnotation(Requires.class);
        if (requires != null) {
            mVersions.put(requires.target(), new Version(requires.version(), required));
        }
        Requirements requirements = cls.getDeclaredAnnotation(Requirements.class);
        if (requirements != null) {
            for (Requires r : requirements.value()) {
                mVersions.put(r.target(), new Version(r.version(), required));
            }
        }
        DependsOn depends = cls.getDeclaredAnnotation(DependsOn.class);
        if (depends != null) {
            addClass(depends.target(), true);
        }
        Dependencies dependencies = cls.getDeclaredAnnotation(Dependencies.class);
        if (dependencies != null) {
            for (DependsOn d : dependencies.value()) {
                addClass(d.target(), true);
            }
        }
    }

    public void checkVersion(VersionInfo plugin) throws InvalidVersionException {
        final ArrayMap<Class<?>, Version> versions = new ArrayMap<>(mVersions);
        plugin.mVersions.forEach(new BiConsumer<Class<?>, Version>() {
            @Override
            public void accept(Class<?> aClass, Version version) {
                Version v = versions.remove(aClass);
                if (v == null) {
                    v = VersionInfo.this.createVersion(aClass);
                }
                if (v == null) {
                    throw new InvalidVersionException(aClass.getSimpleName()
                            + " does not provide an interface", false);
                }
                if (v.mVersion != version.mVersion) {
                    throw new InvalidVersionException(aClass, v.mVersion < version.mVersion,
                            v.mVersion,
                            version.mVersion);
                }
            }
        });
        versions.forEach(new BiConsumer<Class<?>, Version>() {
            @Override
            public void accept(Class<?> aClass, Version version) {
                if (version.mRequired) {
                    throw new InvalidVersionException("Missing required dependency "
                            + aClass.getSimpleName(), false);
                }
            }
        });
    }

    private Version createVersion(Class<?> cls) {
        ProvidesInterface provider = cls.getDeclaredAnnotation(ProvidesInterface.class);
        if (provider != null) {
            return new Version(provider.version(), false);
        }
        return null;
    }

    public <T> boolean hasClass(Class<T> cls) {
        return mVersions.containsKey(cls);
    }

    public static class InvalidVersionException extends RuntimeException {
        private final boolean mTooNew;

        public InvalidVersionException(String str, boolean tooNew) {
            super(str);
            mTooNew = tooNew;
        }

        public InvalidVersionException(Class<?> cls, boolean tooNew, int expected, int actual) {
            super(cls.getSimpleName() + " expected version " + expected + " but had " + actual);
            mTooNew = tooNew;
        }

        public boolean isTooNew() {
            return mTooNew;
        }
    }

    private static class Version {

        private final int mVersion;
        private final boolean mRequired;

        public Version(int version, boolean required) {
            mVersion = version;
            mRequired = required;
        }
    }
}
