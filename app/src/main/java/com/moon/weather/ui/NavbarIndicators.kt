package com.moon.weather.ui

internal data class NavbarIndicator(val page: Int, val scale: Float)

/** Keep the selected city full-size within eight slots; small end dots hint at hidden cities. */
internal fun navbarIndicatorWindow(pageCount: Int, currentPage: Int, previousFirst: Int = 0): List<NavbarIndicator> {
    if (pageCount <= 0) return emptyList()
    val count = minOf(pageCount, 8)
    val selected = currentPage.coerceIn(0, pageCount - 1)
    val previous = previousFirst.coerceIn(0, pageCount - count)
    // Let the selection travel through the window before shifting it. The small
    // continuation hints never replace the full-size selected indicator.
    val first = when {
        selected <= previous && previous > 0 -> selected - 1
        selected >= previous + count - 1 && previous + count < pageCount -> selected - count + 2
        else -> previous
    }.coerceIn(0, pageCount - count)
    val last = first + count - 1
    return (first..last).map { page ->
        val hiddenBefore = page == first && first > 0
        val hiddenAfter = page == last && last < pageCount - 1
        NavbarIndicator(page, if (hiddenBefore || hiddenAfter) .5f else 1f)
    }
}
