/*
 * Copyright (C) 2026 Abdelrahman Hussein (عبدالرحمن حسين)
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
package com.android.quickstep.util;

/**
 * Shared motion tokens for Omni Fusion Motion.
 *
 * Keep launcher-owned transitions consistent without changing global animator scales
 * or claiming control over SystemUI-owned notification transitions.
 */
public final class OmniFusionMotion {
    /** Duration for the standard swipe-to-home workspace reveal. */
    public static final int HOME_REVEAL_DURATION_MS = 320;

    /** Duration for the radial workspace reveal used by the newer Quickstep path. */
    public static final long RADIAL_REVEAL_DURATION_MS = 480L;

    /** Duration for the spring-based staggered workspace reveal fallback. */
    public static final int STAGGERED_HOME_REVEAL_DURATION_MS = 250;

    /** Delay between rows in the staggered workspace reveal. */
    public static final int HOME_REVEAL_ROW_STAGGER_DELAY_MS = 10;

    /** Keep content appearing quickly so the scale motion does not feel sluggish. */
    public static final long CONTENT_FADE_DURATION_MS = 180L;

    private OmniFusionMotion() {
        // Utility class.
    }
}
