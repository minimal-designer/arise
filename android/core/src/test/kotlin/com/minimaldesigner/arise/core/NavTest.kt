package com.minimaldesigner.arise.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NavTest {
    private fun assertClose(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 0.001f) }
    }

    @Test fun `weights move from the start to only the new tab`() {
        val start = listOf(1f, 0f, 0f, 0f)
        assertClose(start, navWeights(start, 3, 0f))
        assertClose(listOf(0.5f, 0f, 0f, 0.5f), navWeights(start, 3, 0.5f))
        assertClose(listOf(0f, 0f, 0f, 1f), navWeights(start, 3, 1f))
        // A tap mid-slide carries on from the weights on screen; the tab in between never opens.
        assertClose(listOf(0.25f, 0f, 0.5f, 0.25f), navWeights(listOf(0.5f, 0f, 0f, 0.5f), 2, 0.5f))
    }

    @Test fun `tabs spread like SpaceAround`() {
        assertClose(listOf(10f, 70f, 110f), spaceAround(listOf(40f, 20f, 20f), 140f))
        // No room left: no gaps, never negative.
        assertClose(listOf(0f, 50f), spaceAround(listOf(50f, 60f), 100f))
        assertEquals(emptyList<Float>(), spaceAround(emptyList(), 100f))
    }

    @Test fun `the pill slides and resizes between tabs`() {
        val xs = listOf(10f, 100f, 200f)
        val ws = listOf(50f, 50f, 110f)
        // Nothing to slide from: straight to the tab.
        assertEquals(PillSpan(200f, 110f), pillSpan(null, xs, ws, 2, 0f))
        val from = PillSpan(10f, 90f)
        assertEquals(from, pillSpan(from, xs, ws, 2, 0f))
        assertEquals(PillSpan(105f, 100f), pillSpan(from, xs, ws, 2, 0.5f))
        assertEquals(PillSpan(200f, 110f), pillSpan(from, xs, ws, 2, 1f))
    }
}
