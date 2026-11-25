# Hive Gallery 1.1.0 (version code 2)

Native Android gallery, Android 10+. This maintenance release retains the original honeycomb, four-direction browsing, black/light themes, bounce physics, staggered entry and floating glass navigation. OTA is disabled.

## Build with one existing GitHub repository (browser only)
1. Unzip the download on your laptop. Open the HiveGallery-V1.1 folder.
2. On your existing repository's default branch (master), upload the complete `app` folder plus `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`. The optional `design` and `scripts` folders contain the SVG sources and conversion utility. Commit replacing the old files. Keep the project files at repository root.
3. Open `.github/workflows/build-apk.yml` on GitHub and replace its full contents with the file of the same name from this archive (hidden folders may need to be shown in your file manager). Commit to master. Do not retain the obsolete build workflow.
4. Actions > Build Hive V1.1 APK > Run workflow > master > Run workflow. Start a new run, not a retry of the old commit.
5. Download the `HiveGallery-v1.1` artifact, extract `HiveGallery-v1.1.apk`, transfer to your phone and install.

Debug builds on fresh CI runners can have different signing certificates. Try installing over 1.0 first. If Android reports a signature conflict, uninstall Hive 1.0 before installing 1.1. This clears Hive's favourites/preferences but does not delete your original device photos/videos. No custom signing keys or secrets required for this test release.

## Layout selector
Open the top-right three-dot menu > Gallery layout. Choose Chronological (day/month/year), Continuous (newest first, no separators), or Continuous (filename A–Z, no date sorting). The choice persists and applies to media grids including album contents and favourites. Albums and Stories overview remain collections.

## Rendering changes
- The visible thumbnail working set is held strongly outside the LRU; queued requests for offscreen items are cancelled where possible. Decode results are bounded to 320px longest side and converted to ARGB_8888. Cache completion runs on main thread and redraws are coalesced.
- Fully offscreen horizontal pages and tiles are not requested. The visible canvas renders directly; the glass uses a separate clipped, cached display list with no thumbnail requests. Snapshot reads do not enqueue work. Unchanged MediaStore query results do not refresh the screen.
- Feather-style SVG assets based on the Feather icon set, converted to Android VectorDrawable files; SVG sources, converter and license included. No network icon requests during use or build.

## Verification checklist
- Stay still on the previously affected page for 10 seconds after loading. No thumbnail should repeatedly turn grey.
- Flick both directions, return to previously visited pages, switch tabs rapidly, open videos, toggle theme.
- Select each layout, restart app, verify saved choice and alphabetical mode; check dates only appear in chronological mode.
- Check nav icons, viewer actions and page arrows.

Static syntax/resource checks were performed on the delivered project. Android SDK compilation, actual device rendering and the signing/install process must be verified by GitHub Actions and the phone. The cache/render changes target likely causes seen in the recording; no physical device profiling was available.
