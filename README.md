# UPSC 400 v2

Offline Android app (Kotlin, Jetpack Compose, SQLite). Session queue, in-app PDF reader, timer, log, V rewards.
No account, no network, no INTERNET permission (the CI build fails if one appears in the merged manifest).

## Build

1. Create a GitHub repository and push this folder (the `.github/workflows/android.yml` file must be at the repository root).
2. Open the Actions tab, run **Build debug APK** (it also runs on every push). It runs the unit tests, builds the APK and checks the manifest.
3. Download the artifact **upsc400-v2-debug-apk**, copy `app-debug.apk` to the phone and install it (allow installs from your file manager).
4. If the build fails, open the log of the failing step and send me the first error lines.

Package id is `com.yash.upsc400v2`, so it installs beside the older app and starts with its own empty data.

## First run

The first launch parses `plan.json` (4 MB, in `app/src/main/assets`) once and seeds the database. After that the plan is never parsed again.

## Using it

* **Queue**: sessions in order (Mathematics, GS, GS, Revision, then the post-GS cycle). The next cycle is highlighted. Open a session, tick pieces, Start/Pause the timer, Done.
* **Skip** swaps with the next session of the same type (Revision sessions are dropped; consolidation and unfinished-carry sessions cannot be skipped). Only possible before time is logged.
* **Done** with unticked pieces creates a short unfinished session at the front of that stream.
* **Library**: attach each book's PDF once (file picker). Set the page offset (PDF page = printed page + offset). **Calibrate chapters** for the ten estimated books: type chapter start pages; ranges are recomputed.
* **Reader**: pinch or double-tap to zoom, Day/Sepia/Night, Paged/Scroll, Go to, bookmarks and chapters, search (Android 15 only; scanned pages have no text), draggable timer chip. The session's page range has a yellow edge strip and a banner on its first page.
* **Log**: V, streak, averages, projected finish, calendar, charts. Tap a day to edit, delete or add time.
* **Settings**: theme, day rollover time, daily reminder, timer notification, backup export/import (JSON, version 2).

## Own mascot images

Drop `mascot_idle.png`, `mascot_focus.png`, `mascot_cheer.png`, `mascot_sleep.png` into `app/src/main/res/drawable-nodpi/` (create the folder). They replace the drawn mascot automatically.

## Rules implemented (see the Step 1 document)

* 1 V per full hour logged, minutes carry. Streak day = 4 h or more on the rollover date; one rest token per 7 days saves a streak across one missed day. Badges at 10, 50, 100, 250 V.
* Reviews at 1, 7 and 30 real days after a topic is completed; a review stays due for 3 days (due day plus 2) and then lapses. At most 4 topics, 8 minutes each; the rest of the 45 minutes is question practice.
* Projected finish = remaining planned time / average hours per day since the first log (zero days counted). Target day 400 is 8 January 2028.

## Tests

`app/src/test/java/.../EngineTest.java` (35 tests, including the real `plan.json`: 652 GS sessions with consolidation, 326 cycles, 37 post-GS cycles, 2,896 planned hours).
Gradle runs them with JUnit. Without Gradle: `sh tools/run-engine-tests.sh` (uses a tiny JUnit stand-in).

## Known limits

* The Android layer (Kotlin, Compose, PDF reader) could not be compiled where this was written. Expect to send me build errors once or twice.
* The reader uses the system PdfRenderer. Bitmaps are bounded by an LRU cache and are not recycled by hand (recycling a bitmap still on screen crashes).
* Lecture rows show the planned minutes and the fraction of the lecture; raw lecture durations are not in plan.json.
