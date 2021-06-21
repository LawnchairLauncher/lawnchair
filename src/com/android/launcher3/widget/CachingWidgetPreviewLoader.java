/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget;

import android.graphics.Bitmap;
import android.os.CancellationSignal;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Wrapper around {@link DatabaseWidgetPreviewLoader} that contains caching logic. */
public class CachingWidgetPreviewLoader implements WidgetPreviewLoader {

    @NonNull private final WidgetPreviewLoader mDelegate;
    @NonNull private final Map<ComponentKey, Map<Size, CacheResult>> mCache = new ArrayMap<>();

    public CachingWidgetPreviewLoader(@NonNull WidgetPreviewLoader delegate) {
        mDelegate = delegate;
    }

    /** Returns whether the preview is loaded for the item and size. */
    public boolean isPreviewLoaded(@NonNull WidgetItem item, @NonNull Size previewSize) {
        return getPreview(item, previewSize) != null;
    }

    /** Returns the cached preview for the item and size, or null if there is none. */
    @Nullable
    public Bitmap getPreview(@NonNull WidgetItem item, @NonNull Size previewSize) {
        CacheResult cacheResult = getCacheResult(item, previewSize);
        if (cacheResult instanceof CacheResult.Loaded) {
            return ((CacheResult.Loaded) cacheResult).mBitmap;
        } else {
            return null;
        }
    }

    @NonNull
    private CacheResult getCacheResult(@NonNull WidgetItem item, @NonNull Size previewSize) {
        synchronized (mCache) {
            Map<Size, CacheResult> cacheResults = mCache.get(toComponentKey(item));
            if (cacheResults == null) {
                return CacheResult.MISS;
            }

            return cacheResults.getOrDefault(previewSize, CacheResult.MISS);
        }
    }

    /**
     * Puts the result in the cache for the item and size. Returns the value previously in the
     * cache, or null if there was none.
     */
    @Nullable
    private CacheResult putCacheResult(
            @NonNull WidgetItem item,
            @NonNull Size previewSize,
            @Nullable CacheResult cacheResult) {
        ComponentKey key = toComponentKey(item);
        synchronized (mCache) {
            Map<Size, CacheResult> cacheResults = mCache.getOrDefault(key, new ArrayMap<>());
            CacheResult previous;
            if (cacheResult == null) {
                previous = cacheResults.remove(previewSize);
                if (cacheResults.isEmpty()) {
                    mCache.remove(key);
                } else {
                    previous = cacheResults.put(previewSize, cacheResult);
                    mCache.put(key, cacheResults);
                }
            } else {
                previous = cacheResults.put(previewSize, cacheResult);
                mCache.put(key, cacheResults);
            }
            return previous;
        }
    }

    private void removeCacheResult(@NonNull WidgetItem item, @NonNull Size previewSize) {
        ComponentKey key = toComponentKey(item);
        synchronized (mCache) {
            Map<Size, CacheResult> cacheResults = mCache.getOrDefault(key, new ArrayMap<>());
            cacheResults.remove(previewSize);
            mCache.put(key, cacheResults);
        }
    }

    /**
     * Gets the preview for the widget item and size, using the value in the cache if stored.
     *
     * @return a {@link CancellationSignal}, which can cancel the request before it loads
     */
    @Override
    @UiThread
    @NonNull
    public CancellationSignal loadPreview(
            @NonNull BaseActivity activity, @NonNull WidgetItem item, @NonNull Size previewSize,
            @NonNull WidgetPreviewLoadedCallback callback) {
        CancellationSignal signal = new CancellationSignal();
        signal.setOnCancelListener(() -> {
            synchronized (mCache) {
                CacheResult cacheResult = getCacheResult(item, previewSize);
                if (!(cacheResult instanceof CacheResult.Loading)) {
                    // If the key isn't actively loading, then this is a no-op. Cancelling loading
                    // shouldn't clear the cache if we've already loaded.
                    return;
                }

                CacheResult.Loading prev = (CacheResult.Loading) cacheResult;
                CacheResult.Loading updated = prev.withoutCallback(callback);

                if (updated.mCallbacks.isEmpty()) {
                    // If the last callback was removed, then cancel the underlying request in the
                    // delegate.
                    prev.mCancellationSignal.cancel();
                    removeCacheResult(item, previewSize);
                } else {
                    // If there are other callbacks still active, then don't cancel the delegate's
                    // request, just remove this callback from the set.
                    putCacheResult(item, previewSize, updated);
                }
            }
        });

        synchronized (mCache) {
            CacheResult cacheResult = getCacheResult(item, previewSize);
            if (cacheResult instanceof CacheResult.Loaded) {
                // If the bitmap is already present in the cache, invoke the callback immediately.
                callback.onPreviewLoaded(((CacheResult.Loaded) cacheResult).mBitmap);
                return signal;
            }

            if (cacheResult instanceof CacheResult.Loading) {
                // If we're already loading the preview for this key, then just add the callback
                // to the set we'll call after it loads.
                CacheResult.Loading prev = (CacheResult.Loading) cacheResult;
                putCacheResult(item, previewSize, prev.withCallback(callback));
                return signal;
            }

            CancellationSignal delegateCancellationSignal =
                    mDelegate.loadPreview(
                            activity,
                            item,
                            previewSize,
                            preview -> {
                                CacheResult prev;
                                synchronized (mCache) {
                                    prev = putCacheResult(
                                            item, previewSize, new CacheResult.Loaded(preview));
                                }
                                if (prev instanceof CacheResult.Loading) {
                                    // Notify each stored callback that the preview has loaded.
                                    ((CacheResult.Loading) prev).mCallbacks
                                            .forEach(c -> c.onPreviewLoaded(preview));
                                } else {
                                    // If there isn't a loading object in the cache, then we were
                                    // notified before adding this signal to the cache. Just
                                    // call back to the provided callback, there can't be others.
                                    callback.onPreviewLoaded(preview);
                                }
                            });
            ArraySet<WidgetPreviewLoadedCallback> callbacks = new ArraySet<>();
            callbacks.add(callback);
            putCacheResult(
                    item,
                    previewSize,
                    new CacheResult.Loading(delegateCancellationSignal, callbacks));
        }

        return signal;
    }

    /** Clears all cached previews for {@code items}, cancelling any in-progress preview loading. */
    public void clearPreviews(Iterable<WidgetItem> items) {
        List<CacheResult> previousCacheResults = new ArrayList<>();
        synchronized (mCache) {
            for (WidgetItem item : items) {
                Map<Size, CacheResult> previousMap = mCache.remove(toComponentKey(item));
                if (previousMap != null) {
                    previousCacheResults.addAll(previousMap.values());
                }
            }
        }

        for (CacheResult previousCacheResult : previousCacheResults) {
            if (previousCacheResult instanceof CacheResult.Loading) {
                ((CacheResult.Loading) previousCacheResult).mCancellationSignal.cancel();
            }
        }
    }

    /** Clears all cached previews, cancelling any in-progress preview loading. */
    public void clearAll() {
        List<CacheResult> previousCacheResults;
        synchronized (mCache) {
            previousCacheResults =
                    mCache
                    .values()
                    .stream()
                    .flatMap(sizeToResult -> sizeToResult.values().stream())
                    .collect(Collectors.toList());
            mCache.clear();
        }

        for (CacheResult previousCacheResult : previousCacheResults) {
            if (previousCacheResult instanceof CacheResult.Loading) {
                ((CacheResult.Loading) previousCacheResult).mCancellationSignal.cancel();
            }
        }
    }

    private abstract static class CacheResult {
        static final CacheResult MISS = new CacheResult() {};

        static final class Loading extends CacheResult {
            @NonNull final CancellationSignal mCancellationSignal;
            @NonNull final Set<WidgetPreviewLoadedCallback> mCallbacks;

            Loading(@NonNull CancellationSignal cancellationSignal,
                    @NonNull Set<WidgetPreviewLoadedCallback> callbacks) {
                mCancellationSignal = cancellationSignal;
                mCallbacks = callbacks;
            }

            @NonNull
            Loading withCallback(@NonNull WidgetPreviewLoadedCallback callback) {
                if (mCallbacks.contains(callback)) return this;
                Set<WidgetPreviewLoadedCallback> newCallbacks =
                        new ArraySet<>(mCallbacks.size() + 1);
                newCallbacks.addAll(mCallbacks);
                newCallbacks.add(callback);
                return new Loading(mCancellationSignal, newCallbacks);
            }

            @NonNull
            Loading withoutCallback(@NonNull WidgetPreviewLoadedCallback callback) {
                if (!mCallbacks.contains(callback)) return this;
                Set<WidgetPreviewLoadedCallback> newCallbacks =
                        new ArraySet<>(mCallbacks.size() - 1);
                for (WidgetPreviewLoadedCallback existingCallback : mCallbacks) {
                    if (!existingCallback.equals(callback)) {
                        newCallbacks.add(existingCallback);
                    }
                }
                return new Loading(mCancellationSignal, newCallbacks);
            }
        }

        static final class Loaded extends CacheResult {
            @NonNull final Bitmap mBitmap;

            Loaded(@NonNull Bitmap bitmap) {
                mBitmap = bitmap;
            }
        }
    }

    @NonNull
    private static ComponentKey toComponentKey(@NonNull WidgetItem item) {
        return new ComponentKey(item.componentName, item.user);
    }
}
