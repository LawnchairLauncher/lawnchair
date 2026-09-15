/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.widget

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R

/**
 * Hosts the members of a [com.android.launcher3.model.data.WidgetStackInfo], showing one at a
 * time with small edge buttons to cycle between them.
 *
 * Long-press-to-drag for the stack as a whole is intercepted here first (mirroring
 * [LauncherAppWidgetHostView]'s [CheckLongPressHelper] use), so a press-and-hold isn't consumed by
 * a member widget's own content before it reaches the workspace drag system.
 */
class WidgetStackHostView(context: Context) : FrameLayout(context), View.OnLongClickListener {

    private val longPressHelper = CheckLongPressHelper(this, this)
    private val memberViews = mutableListOf<View>()

    var activeIndex = 0
        private set

    /** Invoked after a cycle, so the caller can persist the new [activeIndex]. */
    var onActiveIndexChanged: ((Int) -> Unit)? = null

    /** Replaces the hosted member views. [views] must be pre-inflated widget host views. */
    fun setMembers(views: List<View>, initialActiveIndex: Int) {
        removeAllViews()
        memberViews.clear()
        memberViews.addAll(views)
        views.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        activeIndex = if (views.isEmpty()) 0 else initialActiveIndex.coerceIn(0, views.size - 1)
        updateVisibleMember()
        if (views.size > 1) {
            addNavButton(R.drawable.ic_chevron_start, Gravity.START) { cycle(-1) }
            addNavButton(R.drawable.ic_chevron_end, Gravity.END) { cycle(1) }
        }
    }

    private fun addNavButton(iconRes: Int, gravity: Int, onClick: () -> Unit) {
        val size = (28 * resources.displayMetrics.density).toInt()
        val button = ImageView(context).apply {
            setImageResource(iconRes)
            alpha = 0.85f
            isClickable = true
            setOnClickListener { onClick() }
        }
        addView(button, LayoutParams(size, size, gravity or Gravity.CENTER_VERTICAL))
    }

    private fun cycle(delta: Int) {
        if (memberViews.size <= 1) return
        activeIndex = (activeIndex + delta + memberViews.size) % memberViews.size
        updateVisibleMember()
        onActiveIndexChanged?.invoke(activeIndex)
    }

    private fun updateVisibleMember() {
        memberViews.forEachIndexed { index, view -> view.visibility = if (index == activeIndex) VISIBLE else GONE }
    }

    override fun onLongClick(view: View): Boolean {
        view.performLongClick()
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return true
    }
}
