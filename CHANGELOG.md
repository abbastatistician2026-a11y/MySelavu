# Changelog

## Phase 2: A usable app

- **First-time setup**: language, name, salary, month start day, fixed expenses (with suggestions),
  categories with amounts and a live "unplanned money" counter. Every step can be skipped.
- **Home**: real numbers for money left, unplanned money, safe to spend today, spent vs planned,
  category cards with colour warnings (they learn your spending pattern after 2 finished months),
  bills with "paid" ticks, quick-add buttons, recent expenses, no-spend streak, hide amounts.
- **Add expense**: big keypad (type 120+45), category chips ordered by your habits, payment method,
  note, date, "save as quick add". Tap any expense to edit, delete (with Undo) or add again today.
- **Left menu**: History (by day, search, filters, any date range with totals), Month-wise summary,
  Year-wise summary, Salary & income (history, extra income), Fixed expenses, Categories (edit,
  reorder, remove, move money), Suggested plan, Export to Excel, Profile & settings.
- Every change to salary, bills or category amounts asks "from this month or next month?".
  Past months never change. Months roll over automatically, even if the app wasn't opened.
- Excel export with 7 sheets (all data, this month, or a date range).
- Full Tamil translation. New blue wallet app icon.
- TEST app only: Developer tools (demo data, time travel, wipe).

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
