# Changelog

## Phase 1: Foundations (test builds)

- Project set up: `:core` (pure Kotlin engine) and `:app` (Android, Jetpack Compose, Material 3).
- Budget engine with unit tests: money stored as paise, Indian ₹ formatting, the amount keypad
  calculator ("120+45"), budget months with any start day (1–28, including February), "from this month /
  from next month" edits that never change past months, month-end rollover (Move to savings,
  Carry forward, Reset) that also catches up after months of not opening the app, warning colours
  with projections, pattern learning, unusual-expense check, no-spend streak, recurring due dates
  (including 31st-of-month cases), savings goal "per month needed", lent/borrowed with partial repayments.
- Database (Room) for all features, with its schema saved so future updates keep your data.
- App theme (deep teal + saffron, true-black dark mode), custom launcher icon with Android 13 themed-icon
  support, orange TEST badge on the test app.
- English and Tamil text for the preview screen.
- Cloud build: runs all tests, takes screenshots (light/dark, English/Tamil), checks the app has no
  internet permission, reports the APK size and publishes "Test build #N" on the Releases page
  (keeping the newest 5).
