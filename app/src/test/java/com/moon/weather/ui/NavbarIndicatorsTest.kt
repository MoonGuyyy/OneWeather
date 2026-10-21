package com.moon.weather.ui

import org.junit.Assert.*
import org.junit.Test

class NavbarIndicatorsTest {
    @Test
    fun shortListsShowEveryLocationAtFullSize() {
        assertTrue(navbarIndicatorWindow(0, 0).isEmpty())
        for (count in 1..8) for (selected in 0 until count) {
            val window = navbarIndicatorWindow(count, selected)
            assertEquals((0 until count).toList(), window.map { it.page })
            assertTrue(window.all { it.scale == 1f })
        }
    }

    @Test
    fun largeListsKeepTheSelectionVisibleAndMarkOnlyHiddenEdges() {
        for (count in listOf(9, 16, 40)) for (selected in 0 until count) {
            val window = navbarIndicatorWindow(count, selected)
            assertEquals(8, window.size)
            assertEquals(1f, window.single { it.page == selected }.scale, 0f)
            assertEquals((window.first().page..window.last().page).toList(), window.map { it.page })
            assertEquals(if (window.first().page > 0) .5f else 1f, window.first().scale, 0f)
            assertEquals(if (window.last().page < count - 1) .5f else 1f, window.last().scale, 0f)
            assertTrue(window.drop(1).dropLast(1).all { it.scale == 1f })
        }
    }

    @Test
    fun beginningMiddleAndEndUseTheCorrectContinuationHints() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), navbarIndicatorWindow(20, 0).map { it.page })
        val middle = navbarIndicatorWindow(20, 10)
        assertEquals(4, middle.first().page)
        assertEquals(.5f, middle.first().scale, 0f)
        assertEquals(.5f, middle.last().scale, 0f)
        val end = navbarIndicatorWindow(20, 19)
        assertEquals(12, end.first().page)
        assertEquals(19, end.last().page)
        assertEquals(1f, end.last().scale, 0f)
        assertEquals(navbarIndicatorWindow(20, 0), navbarIndicatorWindow(20, -1))
        assertEquals(end, navbarIndicatorWindow(20, 100))
    }

    @Test
    fun selectionTravelsToTheSeventhSlotBeforeTheWindowMoves() {
        var first = 0
        for (selected in 0..6) {
            val window = navbarIndicatorWindow(20, selected, first)
            assertEquals(0, window.first().page)
            first = window.first().page
        }
        val seventhSlot = navbarIndicatorWindow(20, 6, first)
        assertEquals(6, seventhSlot.indexOfFirst { it.page == 6 })
        val shifted = navbarIndicatorWindow(20, 7, first)
        assertEquals(1, shifted.first().page)
        assertEquals(6, shifted.indexOfFirst { it.page == 7 })
        assertEquals(1, navbarIndicatorWindow(20, 5, shifted.first().page).first().page)
        assertEquals(0, navbarIndicatorWindow(20, 1, shifted.first().page).first().page)
    }
}
