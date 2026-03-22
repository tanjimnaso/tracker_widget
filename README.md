# Weight Tracker Widget

A minimal Android home screen widget for tracking weight loss progress. The widget shows your current weight, goal, and a small chart at a glance. Tap it to open the app.

## What it does

The app tracks weigh-ins over time and projects progress toward a goal weight. It uses an exponential moving average to smooth out day-to-day fluctuations and shows two chart views: a 3-month recent view, and a lifelong view that places your weight in context of healthy athletic ranges by age and height.

The widget lives on your home screen and updates whenever you log a weigh-in. The goal is to make progress visible without needing to open anything.

## Features

- Home screen widget, resizable, dark and light mode
- Log weigh-ins by tapping the current weight number
- 3-month chart and lifelong chart, toggle by tapping the graph
- Projections based on recent rate of loss, defaults to 0.6% body weight per week if data is sparse
- Goal weight is suggested automatically based on athletic BMI thresholds, no manual input needed
- Historic weight entries with age input, useful for seeing your trajectory over a longer period
- Height and birth year inputs used for personalised zone boundaries, nothing else

## No ads, no tracking

No analytics, no crash reporting services, no third-party SDKs. Data stays on device. The app has no internet permission.

## Open source

Free to use, modify, and distribute. No license restrictions.

## Built with

- Kotlin, Jetpack Compose, Material Design 3
- Glance for the home screen widget
- Room for local storage
- AI-assisted development (Gemini, Claude)

## Tested on

Samsung Galaxy S22 Ultra, Android 16, One UI 8. Should work on other Android devices with API 26 and above, but untested.
