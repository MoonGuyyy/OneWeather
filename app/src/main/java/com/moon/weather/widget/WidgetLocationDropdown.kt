package com.moon.weather.widget

import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.moon.weather.R
import com.moon.weather.data.WeatherLocation
import kotlin.math.roundToInt

internal data class WidgetLocationOption(val label: String, val location: WeatherLocation?)

/** Content-sized for short lists, bounded and scrollable for long lists and smaller windows. */
internal fun showWidgetLocationDropdown(
    anchor: View,
    options: List<WidgetLocationOption>,
    selectedLocation: WeatherLocation?,
    onSelect: (WeatherLocation?) -> Unit
): PopupWindow {
    val context = anchor.context
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    val inflater = LayoutInflater.from(context)
    val popupView = inflater.inflate(R.layout.widget_config_dropdown_popup,null)
    val container = popupView.findViewById<LinearLayout>(R.id.dropdown_items_container)
    val scroll = popupView.findViewById<ScrollView>(R.id.dropdown_popup_scroll)
    val popup = PopupWindow(popupView,ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,true).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
        elevation = dp(16).toFloat()
        animationStyle = android.R.style.Animation_Dialog
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
    }
    var maxItemWidth = dp(180)
    var selectedItem: View? = null
    val unspecified = View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)
    options.forEach { option ->
        val item = inflater.inflate(R.layout.widget_config_dropdown_item,container,false)
        val text = item.findViewById<TextView>(R.id.dropdown_item_text)
        val check = item.findViewById<ImageView>(R.id.dropdown_item_check)
        val selected = if (option.location == null) selectedLocation == null else
            option.location.lat == selectedLocation?.lat && option.location.lon == selectedLocation?.lon
        text.text = option.label
        text.setTextColor(context.getColor(if (selected) R.color.sesl_accent else R.color.sesl_primary_text))
        text.typeface = Typeface.create(if (selected) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        check.visibility = if (selected) View.VISIBLE else View.GONE
        if (selected) selectedItem = item
        item.setOnClickListener { popup.dismiss(); onSelect(option.location) }
        item.measure(unspecified,unspecified)
        maxItemWidth = maxOf(maxItemWidth,item.measuredWidth)
        container.addView(item)
    }
    val frame = Rect()
    anchor.getWindowVisibleDisplayFrame(frame)
    val availableWidth = (frame.width() - dp(24)).coerceAtLeast(1)
    popup.width = minOf(maxItemWidth + dp(24),availableWidth)
    popupView.measure(View.MeasureSpec.makeMeasureSpec(popup.width,View.MeasureSpec.EXACTLY),unspecified)
    // Explicitly bound the popup; otherwise a clipped wrap-content column cannot reach hidden cities.
    val availableHeight = (popup.getMaxAvailableHeight(anchor,dp(4)) - dp(8)).coerceAtLeast(1)
    popup.height = minOf(popupView.measuredHeight,availableHeight,dp(360))
    popup.showAsDropDown(anchor,0,dp(4),Gravity.END)
    scroll.doOnLayout {
        selectedItem?.let { item ->
            scroll.scrollTo(0,(item.top - (scroll.height - item.height) / 2).coerceAtLeast(0))
        }
    }
    return popup
}
