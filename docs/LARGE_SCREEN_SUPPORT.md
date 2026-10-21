# Tablet and foldable layouts

The app adapts to the **current app window**, including rotation, unfolding, split-screen and DeX resizing. Layouts do not depend on a device model or a physical pixel resolution.

- Weather uses one column below 640dp and two columns above that when each column can remain readable. Larger font settings raise the two-column threshold.
- The weather surface is capped at 1200dp. Search and settings are capped at 640dp. The city menu and its cards fill the window, including square displays; rounded clipping applies only during predictive back. Separating hinges still keep city controls and cards in an unobstructed pane. The navbar background and control row always span the full app window, with buttons inset for system cutouts. In book posture the city indicators shift clear of the hinge.
- Current conditions form a shared header above both forecast columns. The description, high/low and feels-like readings stay directly below the temperature at the left edge, with the original 72dp gap before the forecast cards in windows at least 480dp tall. Hourly and daily cards start at the same height. Each column then stacks its cards with a consistent 16dp gap, independent of the neighboring cards' heights. Hourly, insights, wind/dew point and sunrise/sunset stay on the left; daily, UV/humidity and pressure/visibility stay on the right. Both columns use one shared vertical scroll surface. Single-column windows retain their original card order.
- System bars and cutouts are accounted for. Search also accounts for the keyboard.
- Jetpack WindowManager observes separating folds and occluding hinges while the screen is active. Book posture aligns two weather columns around the hinge when both panes have enough space. Panels and hourly details use a single unobstructed pane. Narrow book panes fall back to one pane.
- In tabletop posture, weather uses the larger unobstructed half. Navigation remains at the full-width bottom edge of the window. The other half retains the weather background.
- City paging, per-city weather scroll, open panels, search text, settings subpage, city edit mode and hourly detail visibility use saved state. Recreating the activity preserves the widget navigation ID so it does not reopen the original widget city.

## Target shapes

| Display | Shape | Representative test window (dp) |
| --- | --- | --- |
| Samsung tablet portrait | 10:16 | 800 × 1280 |
| Samsung tablet landscape | 16:10 | 1280 × 800 |
| Z Fold8 cover | 10:16 | 400 × 640 |
| Z Fold8 main and rotated | 4:3 / 3:4 | 800 × 600 / 600 × 800 |
| Z Fold8 Ultra cover | 9:21 | 360 × 840 |
| Z Fold8 Ultra main and rotated | approximately 10:9 / 9:10 | 800 × 720 / 720 × 800 |
| Split-screen | variable | 480 × 800 |

These are representative logical windows, not claims about the devices' default display density. The Fold8 ratios come from [Samsung Mobile Press](https://api.samsungmobilepress.com/press-releases/galaxy-z-fold8-ultra-fold8-flip8-perfected-every-way-living). Samsung lists the Ultra's main display as 2504 × 2256 and its cover as 2520 × 1080 on its [specification page](https://org-sec-b2c.samsung.com/sec/smartphones/galaxy-z-fold8-ultra/specs/).

## Background artwork and launcher icon

Each of the six weather scenes has a day/night pair, with two additional compositions per image: 2560 × 1600 landscape and 1600 × 2560 portrait. Foldable ratios use a central crop from the corresponding orientation; there are no dedicated assets for each foldable ratio. These 24 WebP assets live alongside the unchanged phone PNGs in `app/src/main/res/drawable-nodpi`.

Large displays select an orientation from the current window. Compact 10:16 cover screens also use the new artwork. Typical tall phone windows retain the original backgrounds in both orientations. Large-display split-screen windows continue to use the new compositions. Rotation and resizing reload the matching resource while retaining the previous frame until loading completes.

The built-in generator returned roughly 1.6-megapixel originals despite the 2.5K prompt. Final exports are enlarged to the requested dimensions with Lanczos interpolation; they are **not native 2.5K generations**. `output/weather-artwork/manifest.json` records every final prompt, native/export dimensions, asset path and checksum. `tools/export_weather_artwork.py` verifies the original phone images have not changed and creates orientation preview sheets.

Adaptive launcher icons preserve the original `ic_launcher_foreground_image.png` artwork. A proportional one-sixth inset compensates for Android's 150% adaptive layer expansion so the original tile fills the visible mask consistently at every launcher size. The fixed 60dp layer and replacement vector artwork have been removed. Legacy pre-Android 8 bitmap launcher icons remain as before.

## Verification

`WeatherAdaptiveLayoutTest` checks window breakpoints, font scaling, width caps, full-width city menus, off-center book hinges, narrow panes, tabletop bounds and folds outside a split-screen window.

`WeatherArtworkShapeTest` checks preservation of normal phone artwork, both tablet/fold orientations, cover screens and resized windows. The clean build, 9 original selected unit tests and all 11 connected layout/recovery/panel tests passed on 2026-10-11. After the full-screen city-menu correction, both debug builds, all 8 layout unit tests and 6 adaptive/city-menu device tests passed again. Results and screenshots are saved under `output/tablet-verification`.

`WeatherAdaptiveInstrumentedTest` fits the target logical windows inside a connected display without changing device settings. It checks card/navigation bounds, resize behavior, saved-state restoration and simulated book/tabletop folds through AndroidX's `WindowLayoutInfoPublisherRule`. City-menu checks cover full content bounds, card width, all four outer corner pixels and selection across square, Fold8 Ultra, tablet portrait/landscape and phone windows. It exports screenshots to the app's external files directory with the `adaptive-` prefix.

After restoring the stacked weather readings and the 72dp spacing above both forecast columns, both debug builds and all 5 adaptive device tests passed again. The checks verify readings below the temperature, their left alignment, the gap above both columns and hinge clearance.

The subsequent spacing correction passed both debug builds and all 6 adaptive device tests. Its additional check measures 16dp between every adjacent pair of cards within each column on portrait/landscape tablets and a Fold8 Ultra window, while verifying that cards stay in their columns. Hourly-detail visibility remains open through changes between one and two columns and saved-state restoration.

Run with JDK 17:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests com.moon.weather.ui.WeatherAdaptiveLayoutTest
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.moon.weather.WeatherAdaptiveInstrumentedTest'
```

The connected Samsung Tab S10+ provides actual tablet execution. Simulated Fold8 shapes and folds complement that testing; physical Fold8 and Fold8 Ultra unfolding, rotation and Samsung taskbar checks are still needed for device-specific validation.
