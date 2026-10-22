<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/04efc1b0-c393-407b-99c4-6a50993d99a6

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.


## CampusConnect v2: sign-in, privacy and limits

This version follows `CampusConnect_Vision_v2_Login_and_Data_Sharing.md`. See **`docs/BACKEND.md`** for the
Supabase database (one merged migration, row-level security, per-role limits, tests) and the GitHub Actions setup.

**Try it (sample accounts, password `Campus#2026`):**

| Who | Email | Notes |
|---|---|---|
| Student | `a.mwangi@campus.ac.tz` | enrolled in the computer science courses |
| Student | `k.tarimo@campus.ac.tz` | |
| Waiting room | `b.juma@campus.ac.tz` | pending: sees only "Awaiting approval" |
| Lecturer | `j.mushi@campus.ac.tz` | teaches CS210, CS215. Needs the second-step code on a new phone |
| Registry (admin) | `admin.registry@campus.ac.tz` | approves accounts. Needs the second-step code every time |

The six-digit codes are shown on screen because this prototype has no mail server. **Open demo** on the
sign-in screen gives the persona switcher; it only exists in demo mode, under a permanent
"DEMO: sample data only" banner. Build with `DEMO_MODE_ENABLED = false` (in `app/build.gradle.kts`) to remove it.

Run the data-layer tests with `gradle :app:testDebugUnitTest`.
