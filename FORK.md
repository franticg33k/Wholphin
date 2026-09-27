# This fork

This is [Wholphin](https://github.com/damontecres/Wholphin) plus a **Cable TV mode**: a full-screen TV for
channels scheduled by the [Cable TV Jellyfin plugin](https://github.com/franticg33k/jellyfin-cable-tv). The TV mode
itself is documented in [`tvmode/README.md`](tvmode/README.md). This file is about keeping the fork easy to update
from upstream.

## Branches

| Branch | Role |
|---|---|
| `main` | An exact mirror of upstream `main`. Never commit to it; the sync workflow fast-forwards it. |
| `cable-tv` | The fork's product branch (make it the repository's default branch): upstream plus the Cable TV mode. |
| feature branches | Work in progress. Open PRs into `cable-tv`, never into `main`. |

Upstream changes reach `cable-tv` by **merging** `main` into it (never rebasing), so every sync is one merge commit
and history is never rewritten.

## Keeping changes upstream-friendly

Rules for any change in this fork:

1. New code goes in the fork's own modules (`:tvmode`, `:tvmode-core`) or in new files, not in upstream files.
2. When an upstream file must change, add a few lines at a hook point. Don't move, rename or reformat upstream code.
3. The app talks to the TV mode only through `TvModeHost` (implemented by `WholphinTvModeHost`). When upstream
   refactors its player, API client or navigation, only that class should need updating.
4. Use upstream's tooling and style: its version catalog, its pre-commit hooks (ktlint), JVM 11 target.
5. Fork-only CI lives in its own workflow files (`cabletv-build.yml`, `upstream-sync.yml`); upstream's workflows are
   left untouched.

### Upstream files this fork changes

These are the only places a sync can conflict (about 44 lines in total):

| File | Change |
|---|---|
| `settings.gradle.kts` | `include(":tvmode-core")`, `include(":tvmode")` |
| `gradle/libs.versions.toml` | `kotlinx-coroutines-core` library entry |
| `app/build.gradle.kts` | `implementation(project(":tvmode"))`; `git describe` ignores a missing tag (can be dropped once the fork has upstream's tags, which the sync workflow copies); `applicationId` is `com.github.damontecres.wholphin.cabletv`, so the fork installs next to upstream's app |
| `ui/nav/Destination.kt` | `Destination.CableTv` |
| `ui/nav/DestinationContent.kt` | branch rendering `CableTvScreen` |
| `ui/nav/NavDrawer.kt` | `NavDrawerItem.CableTv` and its cases in three `when` blocks |
| `services/NavDrawerService.kt` | adds the item when `CableTvAvailability` says the plugin is there |
| `res/values/strings.xml` | `cable_tv` string; `app_name` is "Wholphin Cable TV" |
| `preferences/AppPreference.kt` | the default update URL is this fork's latest release, so in-app updates come from here |
| `services/PlayerFactory.kt` | optional `loadControl` parameter on `createVideoPlayer` (TV mode starts playback after less buffering) |

### Fork-only resources

`app/src/default/res/` holds the fork's launcher icon and TV banner: upstream's cube with a small retro-TV badge, and
"CABLE TV" under the wordmark. Android lays the `default` flavor's resources over `main`, so upstream's icon files
are untouched and merge as usual. If upstream redraws its icon, regenerate these from the new artwork so they don't
fall out of step.

## Syncing with upstream

**Automatically:** the *Sync upstream* workflow runs every Monday (or from the Actions tab):

1. It fast-forwards `main` to upstream and copies upstream's tags.
2. It merges upstream into a `sync/upstream-<date>` branch cut from `cable-tv`.
3. It builds and tests the merge.
4. It opens a PR into `cable-tv` with the result, or an issue listing the files when the merge conflicts.

Merge that PR with a **merge commit**.

One-time setup:

- Make `cable-tv` the default branch (schedules only run from the default branch).
- Settings → Actions → General → allow GitHub Actions to create and approve pull requests.
- Optional: add a `SYNC_TOKEN` secret, a personal access token with `repo` and `workflow` scopes. Without it, a sync
  where upstream changed its workflow files can't be pushed; use GitHub's *Sync fork* button for `main` and merge
  by hand.

**By hand** (or when the workflow reports conflicts):

```sh
git remote add upstream https://github.com/damontecres/Wholphin.git   # once
git fetch upstream --tags
git checkout main && git merge --ff-only upstream/main && git push origin main --tags
git checkout cable-tv && git merge main          # resolve conflicts in the files listed above
./gradlew :tvmode-core:test assembleDefaultDebug testDefaultDebugUnitTest
git push origin cable-tv
```

## Builds

The *Cable TV build* workflow builds every push to `cable-tv` and every PR into it, runs the TV mode tests, and
attaches debug and release APKs to the run (Actions → the run → Artifacts).

Each push to `cable-tv` also replaces the latest GitHub release (tag `cable-tv-latest`, titled with the version,
such as `v1.0.8-27-g1a729c57`) with the new release APKs, so the newest build is always at
`https://github.com/franticg33k/Wholphin/releases/latest/download/Wholphin.apk` (or
`Wholphin-release-arm64-v8a.apk`, `-armeabi-v7a`, `-x86_64`). These names and the title are what the app's update
checker reads, so installed copies offer each new build as an update. Running the workflow by hand on `cable-tv` with
a tag (for example `cable-tv-1.0`) also publishes a permanent release. Upstream's own *PR* workflow also runs on PRs.

### Signing

Release APKs are signed with the repository secrets `KEY_ALIAS`, `KEY_PASSWORD`, `KEY_STORE_PASSWORD` and
`SIGNING_KEY`. Without them CI uses a throwaway key per build, so one build can't update another (in-app or by hand).
To set them up once:

```sh
# 1. Make a keystore (answer the prompts; remember the password). PKCS12 uses one password for store and key.
keytool -genkeypair -v -keystore wholphin-cabletv.keystore -storetype PKCS12 \
  -alias cabletv -keyalg RSA -keysize 4096 -validity 36500

# 2. Base64 it on one line (macOS: base64 -i wholphin-cabletv.keystore | tr -d '\n')
base64 -w0 wholphin-cabletv.keystore > wholphin-cabletv.keystore.b64
```

3. On GitHub: **Settings → Secrets and variables → Actions → New repository secret**, four times:
   `SIGNING_KEY` = the contents of the `.b64` file, `KEY_ALIAS` = `cabletv`, and `KEY_PASSWORD` and
   `KEY_STORE_PASSWORD` = the password. With the GitHub CLI:
   `gh secret set SIGNING_KEY -R franticg33k/Wholphin < wholphin-cabletv.keystore.b64` (and `gh secret set KEY_ALIAS`
   etc., which prompt for the value).
4. Keep the keystore and password somewhere safe and never commit them. If the key is lost, installed copies can't
   update and have to be reinstalled.

The next build is signed with it; builds signed with the throwaway key have to be uninstalled once.
