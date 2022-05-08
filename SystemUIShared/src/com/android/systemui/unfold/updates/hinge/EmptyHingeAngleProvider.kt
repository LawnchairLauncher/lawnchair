package com.android.systemui.unfold.updates.hinge

import androidx.core.util.Consumer

internal class EmptyHingeAngleProvider : HingeAngleProvider {
    override fun start() {
    }

    override fun stop() {
    }

    override fun removeCallback(listener: Consumer<Float>) {
    }

    override fun addCallback(listener: Consumer<Float>) {
    }
}
