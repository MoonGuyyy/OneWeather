package com.moon.weather

import android.widget.Button
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.moon.weather.data.WeatherLocation
import com.moon.weather.widget.WidgetLocationOption
import com.moon.weather.widget.showWidgetLocationDropdown
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetLocationDropdownInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var popup: PopupWindow? = null
    private var selected: WeatherLocation? = null

    private fun show(count: Int): List<WeatherLocation> {
        val cities = (0 until count).map { WeatherLocation("City $it",it.toDouble(),0.0) }
        compose.runOnUiThread { compose.activity.setTheme(R.style.Theme_Weather_WidgetConfiguration) }
        compose.setContent {
            Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
                AndroidView(factory = { context ->
                    Button(context).apply {
                        text = "Choose location"
                        setOnClickListener {
                            popup = showWidgetLocationDropdown(this,
                                listOf(WidgetLocationOption("Current location",null)) +
                                    cities.map { WidgetLocationOption(it.name,it) },selected) { selected = it }
                        }
                    }
                },modifier = Modifier.size(220.dp,56.dp))
            }
        }
        onView(withText("Choose location")).perform(click())
        return cities
    }

    @Test fun aLongListScrollsToTheLastCityAndReopensAtTheSelectedCity() {
        val cities = show(40)
        compose.runOnIdle {
            val window = popup!!
            val scroll = window.contentView.findViewById<ScrollView>(R.id.dropdown_popup_scroll)
            assertTrue("Long selectors must have a bounded viewport",window.height <= 360 * compose.activity.resources.displayMetrics.density + 1)
            assertTrue("The hidden locations must be reachable by scrolling",scroll.canScrollVertically(1))
        }
        onView(withText(cities.last().name)).perform(scrollTo(),click())
        compose.runOnIdle { assertEquals(cities.last(),selected); assertFalse(popup!!.isShowing) }
        onView(withText("Choose location")).perform(click())
        onView(withText(cities.last().name)).check(matches(isDisplayed()))
        onView(withText("Current location")).perform(scrollTo(),click())
        compose.runOnIdle { assertNull(selected); assertFalse(popup!!.isShowing) }
    }

    @Test fun shortListsFitTheirContentAndStillSelectNormally() {
        val cities = show(2)
        compose.runOnIdle {
            val scroll = popup!!.contentView.findViewById<ScrollView>(R.id.dropdown_popup_scroll)
            assertFalse(scroll.canScrollVertically(1))
            assertFalse(scroll.canScrollVertically(-1))
        }
        onView(withText(cities[1].name)).check(matches(isDisplayed())).perform(click())
        compose.runOnIdle { assertEquals(cities[1],selected) }
    }

    @After fun closePopup() { compose.runOnUiThread { popup?.dismiss() } }
}
