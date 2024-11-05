/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:JvmName("CommonAssertions")

package com.android.wm.shell.flicker.utils

import android.graphics.Region
import android.tools.Rotation
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.helpers.WindowUtils
import android.tools.traces.component.IComponentMatcher

fun LegacyFlickerTest.appPairsDividerIsVisibleAtEnd() {
    assertLayersEnd { this.isVisible(APP_PAIR_SPLIT_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.appPairsDividerIsInvisibleAtEnd() {
    assertLayersEnd { this.notContains(APP_PAIR_SPLIT_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.appPairsDividerBecomesVisible() {
    assertLayers {
        this.isInvisible(DOCKED_STACK_DIVIDER_COMPONENT)
            .then()
            .isVisible(DOCKED_STACK_DIVIDER_COMPONENT)
    }
}

fun LegacyFlickerTest.splitScreenEntered(
    component1: IComponentMatcher,
    component2: IComponentMatcher,
    fromOtherApp: Boolean,
    appExistAtStart: Boolean = true
) {
    if (fromOtherApp) {
        if (appExistAtStart) {
            appWindowIsInvisibleAtStart(component1)
        } else {
            appWindowIsNotContainAtStart(component1)
        }
    } else {
        appWindowIsVisibleAtStart(component1)
    }
    if (appExistAtStart) {
        appWindowIsInvisibleAtStart(component2)
    } else {
        appWindowIsNotContainAtStart(component2)
    }
    splitScreenDividerIsInvisibleAtStart()

    appWindowIsVisibleAtEnd(component1)
    appWindowIsVisibleAtEnd(component2)
    splitScreenDividerIsVisibleAtEnd()
}

fun LegacyFlickerTest.splitScreenDismissed(
    component1: IComponentMatcher,
    component2: IComponentMatcher,
    toHome: Boolean
) {
    appWindowIsVisibleAtStart(component1)
    appWindowIsVisibleAtStart(component2)
    splitScreenDividerIsVisibleAtStart()

    appWindowIsInvisibleAtEnd(component1)
    if (toHome) {
        appWindowIsInvisibleAtEnd(component2)
    } else {
        appWindowIsVisibleAtEnd(component2)
    }
    splitScreenDividerIsInvisibleAtEnd()
}

fun LegacyFlickerTest.splitScreenDividerIsVisibleAtStart() {
    assertLayersStart { this.isVisible(SPLIT_SCREEN_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.splitScreenDividerIsVisibleAtEnd() {
    assertLayersEnd { this.isVisible(SPLIT_SCREEN_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.splitScreenDividerIsInvisibleAtStart() {
    assertLayersStart { this.isInvisible(SPLIT_SCREEN_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.splitScreenDividerIsInvisibleAtEnd() {
    assertLayersEnd { this.isInvisible(SPLIT_SCREEN_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.splitScreenDividerBecomesVisible() {
    layerBecomesVisible(SPLIT_SCREEN_DIVIDER_COMPONENT)
}

fun LegacyFlickerTest.splitScreenDividerBecomesInvisible() {
    assertLayers {
        this.isVisible(SPLIT_SCREEN_DIVIDER_COMPONENT)
            .then()
            .isInvisible(SPLIT_SCREEN_DIVIDER_COMPONENT)
    }
}

fun LegacyFlickerTest.layerBecomesVisible(component: IComponentMatcher) {
    assertLayers { this.isInvisible(component).then().isVisible(component) }
}

fun LegacyFlickerTest.layerBecomesInvisible(component: IComponentMatcher) {
    assertLayers { this.isVisible(component).then().isInvisible(component) }
}

fun LegacyFlickerTest.layerIsVisibleAtEnd(component: IComponentMatcher) {
    assertLayersEnd { this.isVisible(component) }
}

fun LegacyFlickerTest.layerKeepVisible(component: IComponentMatcher) {
    assertLayers { this.isVisible(component) }
}

fun LegacyFlickerTest.splitAppLayerBoundsBecomesVisible(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean
) {
    assertLayers {
        this.notContains(SPLIT_SCREEN_DIVIDER_COMPONENT.or(component), isOptional = true)
            .then()
            .isInvisible(SPLIT_SCREEN_DIVIDER_COMPONENT.or(component))
            .then()
            .splitAppLayerBoundsSnapToDivider(
                component,
                landscapePosLeft,
                portraitPosTop,
                scenario.endRotation
            )
    }
}

fun LegacyFlickerTest.splitAppLayerBoundsBecomesVisibleByDrag(component: IComponentMatcher) {
    assertLayers {
        this.notContains(SPLIT_SCREEN_DIVIDER_COMPONENT.or(component), isOptional = true)
            .then()
            .isInvisible(SPLIT_SCREEN_DIVIDER_COMPONENT.or(component))
            .then()
            // TODO(b/245472831): Verify the component should snap to divider.
            .isVisible(component)
    }
}

fun LegacyFlickerTest.splitAppLayerBoundsBecomesInvisible(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean
) {
    assertLayers {
        this.splitAppLayerBoundsSnapToDivider(
                component,
                landscapePosLeft,
                portraitPosTop,
                scenario.endRotation
            )
            .then()
            .isVisible(component, true)
            .then()
            .isInvisible(component)
    }
}

fun LegacyFlickerTest.splitAppLayerBoundsIsVisibleAtEnd(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean
) {
    assertLayersEnd {
        splitAppLayerBoundsSnapToDivider(
            component,
            landscapePosLeft,
            portraitPosTop,
            scenario.endRotation
        )
    }
}

fun LegacyFlickerTest.splitAppLayerBoundsKeepVisible(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean
) {
    assertLayers {
        splitAppLayerBoundsSnapToDivider(
            component,
            landscapePosLeft,
            portraitPosTop,
            scenario.endRotation
        )
    }
}

fun LegacyFlickerTest.splitAppLayerBoundsChanges(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean
) {
    assertLayers {
        if (landscapePosLeft) {
            splitAppLayerBoundsSnapToDivider(
                component,
                landscapePosLeft,
                portraitPosTop,
                scenario.endRotation
            )
        } else {
            splitAppLayerBoundsSnapToDivider(
                    component,
                    landscapePosLeft,
                    portraitPosTop,
                    scenario.endRotation
                )
                .then()
                .isInvisible(component)
                .then()
                .splitAppLayerBoundsSnapToDivider(
                    component,
                    landscapePosLeft,
                    portraitPosTop,
                    scenario.endRotation
                )
        }
    }
}

fun LayersTraceSubject.splitAppLayerBoundsSnapToDivider(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean,
    rotation: Rotation
): LayersTraceSubject {
    return invoke("splitAppLayerBoundsSnapToDivider") {
        it.splitAppLayerBoundsSnapToDivider(component, landscapePosLeft, portraitPosTop, rotation)
    }
}

fun LayerTraceEntrySubject.splitAppLayerBoundsSnapToDivider(
    component: IComponentMatcher,
    landscapePosLeft: Boolean,
    portraitPosTop: Boolean,
    rotation: Rotation
): LayerTraceEntrySubject {
    val displayBounds = WindowUtils.getDisplayBounds(rotation)
    return invoke {
        val dividerRegion =
            layer(SPLIT_SCREEN_DIVIDER_COMPONENT)?.visibleRegion?.region?.bounds
                ?: error("$SPLIT_SCREEN_DIVIDER_COMPONENT component not found")
        visibleRegion(component).isNotEmpty()
        visibleRegion(component)
            .coversAtMost(
                if (displayBounds.width() > displayBounds.height()) {
                    if (landscapePosLeft) {
                        Region(
                            0,
                            0,
                            (dividerRegion.left + dividerRegion.right) / 2,
                            displayBounds.bottom
                        )
                    } else {
                        Region(
                            (dividerRegion.left + dividerRegion.right) / 2,
                            0,
                            displayBounds.right,
                            displayBounds.bottom
                        )
                    }
                } else {
                    if (portraitPosTop) {
                        Region(
                            0,
                            0,
                            displayBounds.right,
                            (dividerRegion.top + dividerRegion.bottom) / 2
                        )
                    } else {
                        Region(
                            0,
                            (dividerRegion.top + dividerRegion.bottom) / 2,
                            displayBounds.right,
                            displayBounds.bottom
                        )
                    }
                }
            )
    }
}

fun LegacyFlickerTest.appWindowBecomesVisible(component: IComponentMatcher) {
    assertWm {
        this.isAppWindowInvisible(component)
            .then()
            .notContains(component, isOptional = true)
            .then()
            .isAppWindowInvisible(component, isOptional = true)
            .then()
            .isAppWindowVisible(component)
    }
}

fun LegacyFlickerTest.appWindowBecomesInvisible(component: IComponentMatcher) {
    assertWm { this.isAppWindowVisible(component).then().isAppWindowInvisible(component) }
}

fun LegacyFlickerTest.appWindowIsVisibleAtStart(component: IComponentMatcher) {
    assertWmStart { this.isAppWindowVisible(component) }
}

fun LegacyFlickerTest.appWindowIsVisibleAtEnd(component: IComponentMatcher) {
    assertWmEnd { this.isAppWindowVisible(component) }
}

fun LegacyFlickerTest.appWindowIsInvisibleAtStart(component: IComponentMatcher) {
    assertWmStart { this.isAppWindowInvisible(component) }
}

fun LegacyFlickerTest.appWindowIsInvisibleAtEnd(component: IComponentMatcher) {
    assertWmEnd { this.isAppWindowInvisible(component) }
}

fun LegacyFlickerTest.appWindowIsNotContainAtStart(component: IComponentMatcher) {
    assertWmStart { this.notContains(component) }
}

fun LegacyFlickerTest.appWindowKeepVisible(component: IComponentMatcher) {
    assertWm { this.isAppWindowVisible(component) }
}

fun LegacyFlickerTest.dockedStackDividerIsVisibleAtEnd() {
    assertLayersEnd { this.isVisible(DOCKED_STACK_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.dockedStackDividerBecomesVisible() {
    assertLayers {
        this.isInvisible(DOCKED_STACK_DIVIDER_COMPONENT)
            .then()
            .isVisible(DOCKED_STACK_DIVIDER_COMPONENT)
    }
}

fun LegacyFlickerTest.dockedStackDividerBecomesInvisible() {
    assertLayers {
        this.isVisible(DOCKED_STACK_DIVIDER_COMPONENT)
            .then()
            .isInvisible(DOCKED_STACK_DIVIDER_COMPONENT)
    }
}

fun LegacyFlickerTest.dockedStackDividerNotExistsAtEnd() {
    assertLayersEnd { this.notContains(DOCKED_STACK_DIVIDER_COMPONENT) }
}

fun LegacyFlickerTest.appPairsPrimaryBoundsIsVisibleAtEnd(
    rotation: Rotation,
    primaryComponent: IComponentMatcher
) {
    assertLayersEnd {
        val dividerRegion =
            layer(APP_PAIR_SPLIT_DIVIDER_COMPONENT)?.visibleRegion?.region
                ?: error("$APP_PAIR_SPLIT_DIVIDER_COMPONENT component not found")
        visibleRegion(primaryComponent).overlaps(getPrimaryRegion(dividerRegion, rotation))
    }
}

fun LegacyFlickerTest.dockedStackPrimaryBoundsIsVisibleAtEnd(
    rotation: Rotation,
    primaryComponent: IComponentMatcher
) {
    assertLayersEnd {
        val dividerRegion =
            layer(DOCKED_STACK_DIVIDER_COMPONENT)?.visibleRegion?.region
                ?: error("$DOCKED_STACK_DIVIDER_COMPONENT component not found")
        visibleRegion(primaryComponent).overlaps(getPrimaryRegion(dividerRegion, rotation))
    }
}

fun LegacyFlickerTest.appPairsSecondaryBoundsIsVisibleAtEnd(
    rotation: Rotation,
    secondaryComponent: IComponentMatcher
) {
    assertLayersEnd {
        val dividerRegion =
            layer(APP_PAIR_SPLIT_DIVIDER_COMPONENT)?.visibleRegion?.region
                ?: error("$APP_PAIR_SPLIT_DIVIDER_COMPONENT component not found")
        visibleRegion(secondaryComponent).overlaps(getSecondaryRegion(dividerRegion, rotation))
    }
}

fun LegacyFlickerTest.dockedStackSecondaryBoundsIsVisibleAtEnd(
    rotation: Rotation,
    secondaryComponent: IComponentMatcher
) {
    assertLayersEnd {
        val dividerRegion =
            layer(DOCKED_STACK_DIVIDER_COMPONENT)?.visibleRegion?.region
                ?: error("$DOCKED_STACK_DIVIDER_COMPONENT component not found")
        visibleRegion(secondaryComponent).overlaps(getSecondaryRegion(dividerRegion, rotation))
    }
}

fun getPrimaryRegion(dividerRegion: Region, rotation: Rotation): Region {
    val displayBounds = WindowUtils.getDisplayBounds(rotation)
    return if (rotation.isRotated()) {
        Region(
            0,
            0,
            dividerRegion.bounds.left + WindowUtils.dockedStackDividerInset,
            displayBounds.bottom
        )
    } else {
        Region(
            0,
            0,
            displayBounds.right,
            dividerRegion.bounds.top + WindowUtils.dockedStackDividerInset
        )
    }
}

fun getSecondaryRegion(dividerRegion: Region, rotation: Rotation): Region {
    val displayBounds = WindowUtils.getDisplayBounds(rotation)
    return if (rotation.isRotated()) {
        Region(
            dividerRegion.bounds.right - WindowUtils.dockedStackDividerInset,
            0,
            displayBounds.right,
            displayBounds.bottom
        )
    } else {
        Region(
            0,
            dividerRegion.bounds.bottom - WindowUtils.dockedStackDividerInset,
            displayBounds.right,
            displayBounds.bottom
        )
    }
}
