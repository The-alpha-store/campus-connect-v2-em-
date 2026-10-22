# CampusConnect backend (Supabase)

Everything the database must enforce lives in **one merged, re-runnable migration**:
`supabase/migrations/20261021000000_campusconnect_v2.sql`.

The app screens are a convenience. The rules below are enforced by the database, so a modified
app or a direct API call gets the same answer: *"You are not allowed to see that."*

## Files

| File | What it is |
|---|---|
| `supabase/migrations/20261021000000_campusconnect_v2.sql` | The whole schema: tables, row-level security, limits, audit log, storage rules, server functions. Safe to run twice. |
| `supabase/seed.sql` | **Development only.** Fake people and sample data. Password for every sample account: `Campus#2026`. Never run it on a real project. |
| `supabase/tests/rls.test.sql` | 105 pgTAP checks of who may see and do what. |
| `supabase/config.toml` | Local and CI settings (email code, password rules, second step). |
| `.github/workflows/ci.yml` | Android build + unit tests, and database migration + tests, on every push and pull request. |
| `.github/workflows/deploy-supabase.yml` | Pushes new migrations to your hosted project (needs approval). |
| `.github/workflows/release-android.yml` | Builds a signed release when you push a tag like `v1.0.0`. |

There were no SQL files or workflows in the uploaded source, so these are new. If you have SQL
elsewhere (for example in the Supabase dashboard), compare it with the migration before running it.

## Who may do what

The limits come from one table, `role_limits`, with a row for **every role and membership status**.
`NULL` means no limit. Change a number there and the rule changes for everyone, without touching the app.

| Membership | Posts per day | Urgent per day | Files shared per day | Largest shared file | Deadlines per day | Hand-ins per day | Largest hand-in | Phones |
|---|---|---|---|---|---|---|---|---|
| **Student, approved** | 0 | 0 | 0 | none | 0 | 20 | 15 MB | 3 |
| **Lecturer, approved** | 30 | 2 | 20 | 50 MB | 10 | 0 | none | 3 |
| **Administrator, approved** | no limit | no limit | no limit | 50 MB (bucket cap) | no limit | no limit | 15 MB (bucket cap) | 5 |
| **Pending** (any role) | 0 | 0 | 0 | none | 0 | 0 | none | 1 |
| **Rejected / Suspended** | 0 | 0 | 0 | none | 0 | 0 | none | 0 |

Days reset at midnight in Tanzania (`Africa/Dar_es_Salaam`).

What each person can see and do (all enforced by row-level security):

| | Student | Lecturer | Administrator |
|---|---|---|---|
| Own profile | yes | yes | yes |
| Other students' profiles | **never** | class list of own courses only (name + number) | yes |
| Timetable | enrolled courses | courses they teach | all |
| Announcements | published, for their audiences | own (drafts too) + audiences | all |
| Post to the whole campus | no | **no** | yes |
| Post to a course | no | only courses they teach | any |
| Post to a programme | no | programmes containing a course they teach | any |
| Urgent priority | no | own course only, 2 a day | yes |
| Study materials | approved, enrolled courses | own courses | all, can remove |
| Deadlines | enrolled courses + registry calendar | own courses | all |
| Deadline "done" ticks | **own only** | **never** (count only) | **never** (count only) |
| Hand-ins | own only | courses they teach | all |
| Notifications | own only | own only | own only |
| Delivered / opened counts | no | own announcements | all |
| Audit log | no | no | read only |
| Approve accounts, assign courses, suspend | no | no | yes (needs second step) |

Other protections built in:

- **Sign-up** accepts only the university email domain (`app_settings.email_domain`). Whatever role or
  status the app sends is ignored: every new account is a **pending student**. Only an administrator can
  change that, through `approve_account`, which can never create an administrator.
- **Administrators need the second step.** `is_admin()` is true only when the sign-in carries `aal2`
  (set `require_admin_mfa` to `false` in `app_settings` only while bootstrapping).
- **Suspending an account works immediately.** Every policy checks the account status on every request.
- **File sizes are read from storage**, not from what the app claims.
- **The audit log is append-only**: no update, delete or truncate, not even by the database owner.
- Room changes and cancellations **must give a reason**, and a room that is already busy is refused.
- Files are private. A file can be read only when the database row that points to it is visible to the reader.
  Folder rules: `materials/<COURSE>/<lecturer uid>/…`, `submissions/<student uid>/<deadline id>/…`,
  `announcements/<author uid>/…`.

## Set up a new Supabase project

1. Create the project at supabase.com.
2. Install the CLI, then: `supabase login` and `supabase link --project-ref <your-ref>`.
3. `supabase db push` (applies the migration; does **not** run the seed).
4. Add your real reference data: rows in `programmes`, `courses`, `programme_courses`,
   `university_students`, `university_staff` (the registry's lists).
5. **Dashboard > Authentication** settings, matching `supabase/config.toml`:
   - Email provider on, **Confirm email** on, OTP length 6, OTP expiry 600 s.
   - Minimum password length 8, require letters and digits. On the Pro plan also switch on
     *leaked password protection*.
   - Multi-factor (TOTP) on.
   - Refresh token rotation on. Leave anonymous sign-ins off.
   - Site URL / redirect: `campusconnect://login-callback`.
6. Create the first administrator:
   1. Sign up in the app with the registry email and verify the code.
   2. In the SQL editor: `select public.bootstrap_first_admin('registry@campus.ac.tz', 'STF-ADM-001');`
      (works only while there is no administrator).
   3. Sign in, enrol in two-step sign-in (TOTP), sign in again. Only then do admin powers switch on.
7. After that, approve every other account from the app's Admin tab (`approve_account`).

## Run and test locally

```bash
supabase start        # needs Docker
supabase db reset     # migration + seed.sql
supabase test db      # runs supabase/tests/*.test.sql
```

## GitHub Actions

Add these under **Settings > Secrets and variables > Actions**:

| Secret | Used by | Where to get it |
|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | deploy | supabase.com > Account > Access Tokens |
| `SUPABASE_DB_PASSWORD` | deploy | the database password you chose for the project |
| `SUPABASE_PROJECT_ID` | deploy | the project reference (Project settings > General) |
| `KEYSTORE_BASE64` | release | `base64 -w0 my-upload-key.jks` |
| `STORE_PASSWORD`, `KEY_PASSWORD` | release | your upload keystore passwords |

Also create an **Environment** called `production` (Settings > Environments) and add yourself as a
required reviewer, so a person approves every production database change.

`ci.yml` runs everywhere. The deploy workflow runs `ci.yml` first, shows a dry run, and only then pushes.

## What is still local in the Android app

The app's sign-in, approval, limits and visibility rules are written to **match** this database, and the
repository tests check them. But the app still keeps its data in memory and does not call Supabase yet:

- `AuthRepository.kt` is a local stand-in. Replace its methods with Supabase Auth calls
  (`signUp` with the metadata keys `full_name`, `id_number`, `is_staff`, `programme`, `department`).
  The emailed code is currently **shown on screen** (`AuthConfig.SHOW_CODES_ON_SCREEN`); a real build sends it by email.
- `CampusRepository.kt` should read and write the tables above. Use `register_device`, `retract_announcement`,
  `remind_unread`, `announcement_stats`, `approve_account`, `my_limits` and `export_my_data` for the server-side parts.
- Nothing survives closing the app yet, and "Will send when online" (an offline queue) is not built.
