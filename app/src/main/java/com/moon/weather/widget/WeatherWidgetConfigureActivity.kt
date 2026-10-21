package com.moon.weather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.SizeF
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.moon.weather.R
import com.moon.weather.data.SettingsRepository
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Samsung One UI styled launcher configuration for Weather widget.
 * Features a stationary live preview with transparent wallpaper cutout,
 * pixel-level top fading on the scroll view, and a genuine One UI dropdown menu.
 */
class WeatherWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingSave = false
    private val widgetBackgroundPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingSave) completeSaveAndClose()
    }
    private lateinit var preview: FrameLayout
    private lateinit var previewContainer: View
    private lateinit var fadingScrollView: FadingNestedScrollView
    private var selectedBackgroundStyle = WidgetBackgroundStyle.ATMOSPHERIC
    private var showBorder = false
    private var selectedCardStyle = WidgetCardStyle.WEATHER
    private var selectedLocation: WeatherLocation? = null
    private var cachedWeatherResponse: WeatherResponse? = null
    private var previewCity: String? = null
    private var previewRequest = 0
    private var useCelsius = true
    private var useMph = false
    private val locationOptions = mutableListOf<WidgetLocationOption>()

    private val cardStyleRadios = mutableListOf<AppCompatRadioButton>()
    private val backgroundRadios = mutableListOf<AppCompatRadioButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSave = savedInstanceState?.getBoolean("pending_widget_save") ?: false
        WeatherWidgetPreviewPublisher.publishIfNeeded(this)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        selectedBackgroundStyle = WeatherWidgetConfigStore.getBackgroundStyle(this, appWidgetId)
        showBorder = WeatherWidgetConfigStore.getShowBorder(this, appWidgetId)
        selectedCardStyle = WeatherWidgetConfigStore.getCardStyle(this, appWidgetId)
        if (selectedCardStyle == WidgetCardStyle.AUTO) {
            selectedCardStyle = WidgetCardStyle.WEATHER
        }
        selectedLocation = WeatherWidgetConfigStore.getLocationSelection(this, appWidgetId)

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.activity_weather_widget_configure)

        val root = findViewById<View>(R.id.widget_config_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val toolbar = findViewById<Toolbar>(R.id.config_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.widget_settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        preview = findViewById(R.id.config_preview)
        previewContainer = findViewById(R.id.config_preview_container)
        fadingScrollView = findViewById(R.id.config_scroll)
        val wallpaperSurface = WallpaperPreviewBackground(getColor(R.color.sesl_background), dp(26).toFloat())
        root.background = wallpaperSurface

        // Real-time wallpaper cutout tracking (preview stays put in place)
        val rootLocation = IntArray(2)
        val previewLocation = IntArray(2)
        val previewArea = Rect()
        val visibleArea = Rect()
        val updateFadingBounds = {
            if (preview.height > 0 && fadingScrollView.height > 0) {
                val scrollLoc = IntArray(2)
                val previewLoc = IntArray(2)
                fadingScrollView.getLocationOnScreen(scrollLoc)
                preview.getLocationOnScreen(previewLoc)
                val previewBottom = ((previewLoc[1] - scrollLoc[1]) + preview.height).toFloat()
                val fadeHeightPx = dp(24).toFloat()
                val targetFadeTop = previewBottom
                if (fadingScrollView.fadeTop != targetFadeTop || fadingScrollView.fadeHeight != fadeHeightPx) {
                    fadingScrollView.fadeTop = targetFadeTop
                    fadingScrollView.fadeHeight = fadeHeightPx
                }
            }
        }

        root.viewTreeObserver.addOnPreDrawListener {
            root.getLocationOnScreen(rootLocation)
            preview.getLocationOnScreen(previewLocation)
            previewArea.set(
                previewLocation[0] - rootLocation[0],
                previewLocation[1] - rootLocation[1],
                previewLocation[0] - rootLocation[0] + preview.width,
                previewLocation[1] - rootLocation[1] + preview.height
            )
            if (preview.getGlobalVisibleRect(visibleArea)) {
                visibleArea.offset(-rootLocation[0], -rootLocation[1])
            } else visibleArea.setEmpty()
            wallpaperSurface.updatePreview(previewArea, visibleArea)
            updateFadingBounds()
            true
        }

        preview.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updateFadingBounds()
                preview.post { renderPreview() }
            }
        }

        // Dynamically adjust top spacer and fading shader to match pinned preview height
        previewContainer.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height > 0 && height != oldBottom - oldTop) {
                findViewById<View>(R.id.config_preview_spacer)?.apply {
                    if (layoutParams.height != height) {
                        layoutParams.height = height
                        requestLayout()
                    }
                }
                updateFadingBounds()
            }
        }

        // Action buttons layout adjustment to avoid overlapping scroll content
        findViewById<View>(R.id.config_actions).addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            fadingScrollView.apply {
                val bottom = view.height + dp(32)
                if (paddingBottom != bottom) setPadding(paddingLeft, paddingTop, paddingRight, bottom)
            }
        }

        findViewById<View>(R.id.config_cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.config_save).setOnClickListener { saveAndClose() }

        setupLocationDropdownButton()
        setupCardStyleSection()
        setupBackgroundSection()

        lifecycleScope.launch {
            useCelsius = SettingsRepository(this@WeatherWidgetConfigureActivity).useCelsius.first()
            useMph = SettingsRepository(this@WeatherWidgetConfigureActivity).useMph.first()
            loadLocations()
            renderPreview()
        }

        refreshPreviewWeather()
    }

    private fun setupLocationDropdownButton() {
        val button = findViewById<View>(R.id.config_location_dropdown_button)
        button.setOnClickListener {
            showSamsungLocationDropdown(button)
        }
        findViewById<View>(R.id.config_location_card)?.setOnClickListener {
            showSamsungLocationDropdown(button)
        }
        updateSelectedLocationText()
    }

    private fun updateSelectedLocationText() {
        val label = if (selectedLocation == null) {
            getString(R.string.widget_location_current)
        } else {
            selectedLocation?.name ?: getString(R.string.widget_location_current)
        }
        findViewById<TextView>(R.id.config_location_selected_text).text = label
    }

    /**
     * Genuine Samsung One UI dropdown popup:
     * - Right-aligned to the screen / right under the dropdown button
     * - Width wraps content ("Just as much as it needs")
     * - Active item has accent blue text and blue checkmark
     * - Inactive item has primary text and no checkmark
     */
    private fun showSamsungLocationDropdown(anchor: View) {
        if (locationOptions.isEmpty()) return
        showWidgetLocationDropdown(anchor, locationOptions, selectedLocation) { location ->
            selectedLocation = location
            updateSelectedLocationText()
            cachedWeatherResponse = null
            previewCity = selectedLocation?.name
            renderPreview()
            refreshPreviewWeather()
        }
    }

    private suspend fun loadLocations() {
        val savedLocations = WeatherWidgetDataSource.getSelectableLocations(this)
        locationOptions.clear()
        locationOptions += WidgetLocationOption(getString(R.string.widget_location_current), null)
        locationOptions += savedLocations.map { WidgetLocationOption(it.name, it) }
        updateSelectedLocationText()
    }

    private fun refreshPreviewWeather() {
        val request = ++previewRequest
        val location = selectedLocation
        lifecycleScope.launch {
            val result = runCatching {
                WeatherWidgetDataSource.fetchPreviewWeather(this@WeatherWidgetConfigureActivity, appWidgetId, location)
            }.getOrNull()
            if (request != previewRequest) return@launch
            cachedWeatherResponse = result?.second
            previewCity = result?.first?.name ?: location?.name
            renderPreview()
        }
    }

    private fun setupCardStyleSection() {
        val group = findViewById<LinearLayout>(R.id.config_card_styles)
        group.removeAllViews()
        cardStyleRadios.clear()

        // Adaptive style removed per user request
        val styles = listOf(
            Triple(WidgetCardStyle.WEATHER, R.string.widget_layout_weather, R.string.widget_layout_weather_summary),
            Triple(WidgetCardStyle.FORECAST, R.string.widget_layout_forecast, R.string.widget_layout_forecast_summary),
            Triple(WidgetCardStyle.SUNSET, R.string.widget_layout_sunset, R.string.widget_layout_sunset_summary),
            Triple(WidgetCardStyle.WIND, R.string.widget_layout_wind, R.string.widget_layout_wind_summary)
        )

        styles.forEachIndexed { index, (style, titleRes, summaryRes) ->
            if (index > 0) addDivider(group)
            val isSelected = style == selectedCardStyle
            val radio = addRadioRow(
                group = group,
                title = getString(titleRes),
                summary = getString(summaryRes),
                isSelected = isSelected
            ) {
                selectedCardStyle = style
                cardStyleRadios.forEachIndexed { i, r -> r.isChecked = (styles[i].first == style) }
                renderPreview()
            }
            cardStyleRadios.add(radio)
        }
    }

    private fun setupBackgroundSection() {
        val group = findViewById<LinearLayout>(R.id.config_background_styles)
        group.removeAllViews()
        backgroundRadios.clear()

        val backgrounds = listOf(
            Triple(WidgetBackgroundStyle.ATMOSPHERIC, R.string.widget_bg_atmospheric, R.string.widget_bg_atmospheric_summary),
            Triple(WidgetBackgroundStyle.GLASS, R.string.widget_bg_blurred, R.string.widget_bg_blurred_summary),
            Triple(WidgetBackgroundStyle.DARK_BLUE, R.string.widget_bg_dark_blue, R.string.widget_bg_dark_blue_summary),
            Triple(WidgetBackgroundStyle.LIGHT_BLUE, R.string.widget_bg_light_blue, R.string.widget_bg_light_blue_summary)
        )

        backgrounds.forEachIndexed { index, (style, titleRes, summaryRes) ->
            if (index > 0) addDivider(group)
            val isSelected = style == selectedBackgroundStyle
            val radio = addRadioRow(
                group = group,
                title = getString(titleRes),
                summary = getString(summaryRes),
                isSelected = isSelected
            ) {
                selectedBackgroundStyle = style
                backgroundRadios.forEachIndexed { i, r -> r.isChecked = (backgrounds[i].first == style) }
                renderPreview()
            }
            backgroundRadios.add(radio)
        }
        addDivider(group)
        val borderSwitch = SwitchCompat(this).apply {
            text = getString(R.string.widget_show_border)
            textSize = 16f
            setTextColor(getColor(R.color.sesl_primary_text))
            isChecked = showBorder
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnCheckedChangeListener { _, checked ->
                showBorder = checked
                renderPreview()
            }
        }
        group.addView(borderSwitch, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun addRadioRow(
        group: LinearLayout,
        title: String,
        summary: String?,
        isSelected: Boolean,
        onSelect: () -> Unit
    ): AppCompatRadioButton {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            val selectable = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
            setBackgroundResource(selectable.resourceId)
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(getColor(R.color.sesl_primary_text))
            })
            if (summary != null) {
                addView(TextView(context).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(getColor(R.color.sesl_secondary_text))
                    setPadding(0, dp(2), 0, 0)
                })
            }
        }

        row.addView(textLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(16)
        })

        val radio = AppCompatRadioButton(this).apply {
            isChecked = isSelected
            isClickable = false
            supportButtonTintList = ColorStateList.valueOf(getColor(R.color.sesl_accent))
        }
        row.addView(radio)

        row.setOnClickListener {
            onSelect()
        }

        group.addView(row)
        return radio
    }

    private fun addDivider(group: LinearLayout) {
        group.addView(
            View(this).apply { setBackgroundColor(getColor(R.color.sesl_divider)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                marginStart = dp(20)
                marginEnd = dp(20)
            }
        )
    }

    private fun renderPreview() {
        if (preview.width == 0 || preview.height == 0) return
        val manager = AppWidgetManager.getInstance(this)
        val options = intent.getBundleExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS)
            ?: manager.getAppWidgetOptions(appWidgetId)
        val widgetInfo = manager.getAppWidgetInfo(appWidgetId)
        val density = resources.displayMetrics.density
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val exactSizes = if (android.os.Build.VERSION.SDK_INT >= 31)
            BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES,
                SizeF::class.java) else null
        val selectedWidth = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val selectedHeight = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        // One UI binds the widget with its selected size flag before it supplies pixel bounds.
        val samsungSize = when (options.getInt("semWidgetSize")) {
            2 -> WidgetPickerCatalog.compact
            4 -> WidgetPickerCatalog.strip
            8 -> WidgetPickerCatalog.square
            16 -> WidgetPickerCatalog.large
            else -> null
        }
        val fallback = samsungSize ?: WidgetPickerCatalog.entry(widgetInfo)?.defaultSize ?: WidgetPickerCatalog.large
        // A host may report every supported size. Choose the active portrait/landscape dimensions,
        // rather than always choosing the smallest width (which was always the 2x1 preview).
        val exactSize = if (selectedWidth > 0 && selectedHeight > 0) exactSizes?.minByOrNull {
            kotlin.math.abs(it.width - selectedWidth) + kotlin.math.abs(it.height - selectedHeight)
        } else null
        val width = samsungSize?.width?.toInt() ?: exactSize?.width?.toInt()
            ?: options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
            ?: fallback.width.toInt()
        val height = samsungSize?.height?.toInt() ?: exactSize?.height?.toInt()
            ?: options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }
            ?: fallback.height.toInt()

        val locationName = selectedLocation?.name ?: previewCity ?: getString(R.string.widget_location_current)

        WeatherWidgetArtwork.initializeFont(this)
        val artworkBitmap = WeatherWidgetArtwork.render(
            context = this,
            widthDp = width,
            heightDp = height,
            city = locationName,
            weather = cachedWeatherResponse,
            cardStyle = selectedCardStyle,
            backgroundStyle = selectedBackgroundStyle,
            useCelsius = useCelsius,
            showBorder = showBorder,
            useMph = useMph
        )

        val host = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = WidgetShape.radius(height.toFloat()) * resources.displayMetrics.density
                val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                setColor(if (selectedBackgroundStyle == WidgetBackgroundStyle.GLASS) {
                    WeatherWidgetArtwork.glassTint(isNight)
                } else Color.TRANSPARENT)
            }
            clipToOutline = true
            val iv = ImageView(context).apply {
                setImageBitmap(artworkBitmap)
                scaleType = ImageView.ScaleType.FIT_XY
            }
            addView(iv, FrameLayout.LayoutParams(-1, -1))
            addView(View(context).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))
        }

        val scale = minOf(1f, (preview.width - dp(40)).toFloat() / dp(width), (preview.height - dp(40)).toFloat() / dp(height)).coerceAtLeast(.1f)
        val displayWidth = (dp(width) * scale).toInt()
        val displayHeight = (dp(height) * scale).toInt()
        (host.background as GradientDrawable).cornerRadius = WidgetShape.radius(height.toFloat()) * density * scale
        preview.removeAllViews()
        preview.addView(host, FrameLayout.LayoutParams(displayWidth, displayHeight, Gravity.CENTER))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveAndClose() {
        if (!WeatherWidgetConfigStore.saveConfiguration(this, appWidgetId, selectedBackgroundStyle,
                showBorder, selectedCardStyle, selectedLocation)) {
            Toast.makeText(this, R.string.widget_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // Show the configured city and the preview's current weather before closing.
        // The receiver then fetches fresh weather without replacing this with --°.
        runCatching {
            WeatherWidgetProvider.updateConfiguredWidgetNow(this, appWidgetId,
                selectedLocation?.name ?: previewCity, cachedWeatherResponse, useCelsius, useMph)
        }.onFailure { Log.w("WeatherWidget", "Could not apply saved widget immediately", it) }

        WeatherWidgetRefreshScheduler.ensureScheduled(this)
        WeatherWidgetRefreshScheduler.enqueueRefresh(this)
        pendingSave = true
        if (!WeatherWidgetBatterySettings.request(this, automatic = true) { widgetBackgroundPermission.launch(it) }) {
            completeSaveAndClose()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_widget_save", pendingSave)
        super.onSaveInstanceState(outState)
    }

    private fun completeSaveAndClose() {
        pendingSave = false
        val updateIntent = Intent().apply {
            component = AppWidgetManager.getInstance(this@WeatherWidgetConfigureActivity)
                .getAppWidgetInfo(appWidgetId)?.provider
                ?: android.content.ComponentName(this@WeatherWidgetConfigureActivity, WeatherWidgetProvider::class.java)
            action = WeatherWidgetProvider.ACTION_CONFIG_CHANGED
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        WeatherWidgetRefreshScheduler.ensureScheduled(this)
        WeatherWidgetRefreshScheduler.enqueueRefresh(this)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
        applicationContext.sendBroadcast(updateIntent)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
