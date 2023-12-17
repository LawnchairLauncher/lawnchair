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
package com.android.launcher3.util.viewcapture_analysis;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Detector of one kind of anomaly.
 */
abstract class AnomalyDetector {
    // Index of this detector in ViewCaptureAnalyzer.ANOMALY_DETECTORS
    public int detectorOrdinal;

    /**
     * Element of the tree of ignored nodes.
     * If the "children" map is empty, then this node should be ignored, i.e. the analysis shouldn't
     * run for it.
     * I.e. ignored nodes correspond to the leaves in the ignored nodes tree.
     */
    protected static class IgnoreNode {
        // Map from child node identities to ignore-nodes for these children.
        public final Map<String, IgnoreNode> children = new HashMap<>();
    }

    // Converts the list of full paths of nodes to ignore to a more efficient tree of ignore-nodes.
    protected static IgnoreNode buildIgnoreNodesTree(Iterable<String> pathsToIgnore) {
        final IgnoreNode root = new IgnoreNode();
        for (String pathToIgnore : pathsToIgnore) {
            // Scan the diag path of an ignored node and add its elements into the tree.
            IgnoreNode currentIgnoreNode = root;
            for (String part : pathToIgnore.split("\\|")) {
                // Ensure that the child of the node is added to the tree.
                IgnoreNode child = currentIgnoreNode.children.get(part);
                if (child == null) {
                    currentIgnoreNode.children.put(part, child = new IgnoreNode());
                }
                currentIgnoreNode = child;
            }
        }
        return root;
    }

    /**
     * Initializes fields of the node that are specific to the anomaly detected by this
     * detector.
     */
    abstract void initializeNode(@NonNull ViewCaptureAnalyzer.AnalysisNode info);

    /**
     * Detects anomalies by looking at the last occurrence of a view, and the current one.
     * null value means that the view. 'oldInfo' and 'newInfo' cannot be both null.
     * If an anomaly is detected, an exception will be thrown.
     *
     * @param oldInfo      the view, as seen in the last frame that contained it in the view
     *                     hierarchy before 'currentFrame'. 'null' means that the view is first seen
     *                     in the 'currentFrame'.
     * @param newInfo      the view in the view hierarchy of the 'currentFrame'. 'null' means that
     *                     the view is not present in the 'currentFrame', but was present in the
     *                     previous frame.
     * @param frameN       number of the current frame.
     * @param windowSizePx maximum of the window width and height, in pixels.
     * @return Anomaly diagnostic message if an anomaly has been detected; null otherwise.
     */
    abstract String detectAnomalies(
            @Nullable ViewCaptureAnalyzer.AnalysisNode oldInfo,
            @Nullable ViewCaptureAnalyzer.AnalysisNode newInfo, int frameN,
            long frameTimeNs, int windowSizePx);
}
