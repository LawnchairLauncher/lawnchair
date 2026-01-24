/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_BUBBLE_BAR;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarSharedState;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController.TaskbarViewPropertiesProvider;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleBarLocationOnDemandListener;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.RunnableList;
import com.android.quickstep.SystemUiProxy;
import com.android.wm.shell.shared.bubbles.DragZoneFactory;

import java.io.PrintWriter;
import java.util.Optional;

/** Hosts various bubble controllers to facilitate passing between one another. */
public class BubbleControllers {

    public final BubbleBarController bubbleBarController;
    public final BubbleBarViewController bubbleBarViewController;
    public final BubbleStashController bubbleStashController;
    public final Optional<BubbleStashedHandleViewController> bubbleStashedHandleViewController;
    public final BubbleDragController bubbleDragController;
    public final BubbleDismissController bubbleDismissController;
    public final BubbleBarPinController bubbleBarPinController;
    public final BubblePinController bubblePinController;
    public final Optional<BubbleBarSwipeController> bubbleBarSwipeController;
    public final BubbleCreator bubbleCreator;
    public final DragToBubbleController dragToBubbleController;

    private final RunnableList mPostInitRunnables = new RunnableList();

    /**
     * Want to add a new controller? Don't forget to:
     * * Call init
     * * Call onDestroy
     */
    public BubbleControllers(
            BubbleBarController bubbleBarController,
            BubbleBarViewController bubbleBarViewController,
            BubbleStashController bubbleStashController,
            Optional<BubbleStashedHandleViewController> bubbleStashedHandleViewController,
            BubbleDragController bubbleDragController,
            BubbleDismissController bubbleDismissController,
            BubbleBarPinController bubbleBarPinController,
            BubblePinController bubblePinController,
            Optional<BubbleBarSwipeController> bubbleBarSwipeController,
            DragToBubbleController dragToBubbleController,
            BubbleCreator bubbleCreator) {
        this.bubbleBarController = bubbleBarController;
        this.bubbleBarViewController = bubbleBarViewController;
        this.bubbleStashController = bubbleStashController;
        this.bubbleStashedHandleViewController = bubbleStashedHandleViewController;
        this.bubbleDragController = bubbleDragController;
        this.bubbleDismissController = bubbleDismissController;
        this.bubbleBarPinController = bubbleBarPinController;
        this.bubblePinController = bubblePinController;
        this.bubbleBarSwipeController = bubbleBarSwipeController;
        this.bubbleCreator = bubbleCreator;
        this.dragToBubbleController = dragToBubbleController;
    }

    /**
     * Initializes all controllers. Note that controllers can now reference each
     * other through this
     * BubbleControllers instance, but should be careful to only access things that
     * were created
     * in constructors for now, as some controllers may still be waiting for init().
     */
    public void init(TaskbarSharedState taskbarSharedState, TaskbarControllers taskbarControllers) {
        BubbleBarLocationCompositeListener bubbleBarLocationListeners =
                new BubbleBarLocationCompositeListener(
                        taskbarControllers.navbarButtonsViewController,
                        taskbarControllers.taskbarViewController,
                        new BubbleBarLocationOnDemandListener(() -> taskbarControllers.uiController)
                );
        bubbleBarController.init(this,
                bubbleBarLocationListeners,
                taskbarSharedState);
        bubbleStashedHandleViewController.ifPresent(
                controller -> controller.init(/* bubbleControllers = */ this));
        bubbleStashController.init(
                taskbarControllers.taskbarInsetsController,
                bubbleBarViewController,
                bubbleStashedHandleViewController.orElse(null),
                taskbarControllers::runAfterInit
        );
        bubbleBarViewController.init(taskbarControllers, /* bubbleControllers = */ this,
                new TaskbarViewPropertiesProvider() {
                    @Override
                    public Rect getTaskbarViewBounds() {
                        return taskbarControllers.taskbarViewController
                                .getTransientTaskbarIconLayoutBoundsInParent();
                    }

                    @Override
                    public MultiPropertyFactory<View>.MultiProperty getIconsAlpha() {
                        return taskbarControllers.taskbarViewController
                                .getTaskbarIconAlpha()
                                .get(ALPHA_INDEX_BUBBLE_BAR);
                    }
                });
        bubbleDragController.init(/* bubbleControllers = */ this, bubbleBarLocationListeners);
        bubbleDismissController.init(/* bubbleControllers = */ this);
        bubbleBarPinController.init(this, bubbleBarLocationListeners);
        bubblePinController.init(this);
        bubbleBarSwipeController.ifPresent(c -> c.init(this));
        dragToBubbleController.init(bubbleBarViewController,
                new DragZoneFactory.BubbleBarPropertiesProvider() {
                    @Override
                    public int getHeight() {
                        return (int) bubbleBarViewController.getBubbleBarCollapsedHeight();
                    }

                    @Override
                    public int getWidth() {
                        return (int) bubbleBarViewController.getBubbleBarCollapsedWidth();
                    }

                    @Override
                    public int getBottomPadding() {
                        return -(int) bubbleStashController.getBubbleBarTranslationY();
                    }
                },
                bubbleBarLocationListeners,
                SystemUiProxy.INSTANCE.get(taskbarControllers.taskbarActivityContext));
        mPostInitRunnables.executeAllAndDestroy();
    }

    /**
     * If all controllers are already initialized, runs the given callback
     * immediately. Otherwise,
     * queues it to run after calling init() on all controllers. This should likely
     * be used in any
     * case where one controller is telling another controller to do something
     * inside init().
     */
    public void runAfterInit(Runnable runnable) {
        // If this has been executed in init, it automatically runs adds to it.
        mPostInitRunnables.add(runnable);
    }

    /**
     * Cleans up all controllers.
     */
    public void onDestroy() {
        bubbleStashedHandleViewController.ifPresent(BubbleStashedHandleViewController::onDestroy);
        bubbleBarController.onDestroy();
        bubbleBarViewController.onDestroy();
    }

    /** Dumps bubble controllers state. */
    public void dump(PrintWriter pw) {
        bubbleBarViewController.dump(pw);
    }
}
