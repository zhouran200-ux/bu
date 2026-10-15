package com.lucent.app.ui

import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

class ScaleIndicationTest {
    @Test fun equivalentFactoriesDoNotRestartTheIndication() {
        val first = ScaleIndicationNodeFactory(snap(), 0.985f)
        val second = ScaleIndicationNodeFactory(snap(), 0.985f)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, ScaleIndicationNodeFactory(snap(), 1f))
    }

    @Test fun factoriesCreateIndependentNodesAndReuseResetsScale() {
        val factory = ScaleIndicationNodeFactory(snap())
        val first = factory.create(MutableInteractionSource()) as ScaleIndicationNode
        val second = factory.create(MutableInteractionSource()) as ScaleIndicationNode
        assertNotSame(first, second)
        val oldState = first.animatedScalePercent
        first.onReset()
        assertNotSame(oldState, first.animatedScalePercent)
        assertEquals(1f, first.animatedScalePercent.value)
    }
}
