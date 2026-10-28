# APRIL settings bridge (isolated test branch)

This patch adds a native deep-link receiver and connects three stored percentages to the existing live-wallpaper renderer. It does not alter the layered artwork or the existing visual composition.

## Settings link format

The companion app must open a URL with the current values when Save Settings is tapped:

`april://settings?gyro=50&parallax=60&motion=40`

Query values are integers from 0 to 100:
- `gyro`: gyroscope sensitivity (default 50)
- `parallax`: parallax depth (default 60)
- `motion`: motion strength (default 40)

The APRIL app stores the values in its own private SharedPreferences. The wallpaper renderer reads them and responds to changes while running. Opening the settings link returns to the calling app after saving. Opening APRIL normally still opens Android's live-wallpaper picker.

This is only the APRIL-side bridge. The Adalo Save Settings action must be configured to open the URL using the three current slider values; that action is not changed by this repository patch.
