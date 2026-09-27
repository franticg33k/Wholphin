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

These are the only places a sync can conflict (about 41 lines in total):

| File | Change |
|---|---|
| `settings.gradle.kts` | `include(":tvmode-core")`, `include(":tvmode")` |
| `gradle/libs.versions.toml` | `kotlinx-coroutines-core` library entry |
| `app/build.gradle.kts` | `implementation(project(":tvmode"))`; `git describe` ignores a missing tag (can be dropped once the fork has upstream's tags, which the sync workflow copies) |
| `ui/nav/Destination.kt` | `Destination.CableTv` |
| `ui/nav/DestinationContent.kt` | branch rendering `CableTvScreen` |
| `ui/nav/NavDrawer.kt` | `NavDrawerItem.CableTv` and its cases in three `when` blocks |
| `services/NavDrawerService.kt` | adds the item when `CableTvAvailability` says the plugin is there |
| `res/values/strings.xml` | `cable_tv` string |
| `services/PlayerFactory.kt` | optional `loadControl` parameter on `createVideoPlayer` (TV mode starts playback after less buffering) |

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
attaches debug and release APKs to the run (Actions → the run → Artifacts). Each push to `cable-tv` also
replaces the [`cable-tv-latest` release](https://github.com/franticg33k/Wholphin/releases/tag/cable-tv-latest) with the new release APKs, so the newest
build is always at `https://github.com/franticg33k/Wholphin/releases/latest/download/Wholphin-CableTV.apk` (or `-arm64-v8a`, `-armeabi-v7a`, `-x86_64`).
Running the workflow by hand on `cable-tv` with a tag (for example `cable-tv-1.0`) also publishes a permanent release. Release APKs use the
`KEY_ALIAS`, `KEY_PASSWORD`, `KEY_STORE_PASSWORD` and `SIGNING_KEY` secrets when set (base64 keystore in
`SIGNING_KEY`), otherwise a throwaway debug key, so builds can't update each other until the secrets are added. Upstream's own *PR* workflow also runs on PRs.
