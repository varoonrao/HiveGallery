# Hive Gallery 1.2.0 (version code 3)

Native Android 10+ gallery, continuing V1.1. OTA is disabled. Package remains `com.hive.gallery`. Existing honeycomb layout choices, navigation, theme, favourites and motion controls remain.

## Build on GitHub — laptop browser only
1. Extract the supplied HiveGallery-V1.2.zip. Open the `HiveGallery-V1.2` folder, not its parent.
2. In your existing GitHub repository on `master`, upload the `app` folder and root `build.gradle`, `settings.gradle`, `gradle.properties` and `README.md`, replacing the older versions. Include all files inside app, especially the two new Java classes and new drawable resources. `design` and `scripts` are optional for building.
3. Replace `.github/workflows/build-apk.yml` with the included version (or the separately provided YAML). Commit to master. The Android SDK setup action remains in the workflow.
4. Actions > Build Hive V1.2 APK > Run workflow > master > Run workflow. Start a NEW run, not a retry of the V1.1 commit.
5. Download `HiveGallery-v1.2` under Artifacts, unzip it, and install `HiveGallery-v1.2.apk` on your phone.

No Android Studio, Codespaces, Termux, signing secrets, OTA repository or other software is required. This is debug-signed. Fresh GitHub runners may use different debug keys; if Android reports a signing conflict, uninstall the older Hive app before installing V1.2. This resets Hive preferences and favourites but does not delete your original media library.

## Viewer changes
- `HoneycombView.boundsInWindow` exposes the actual last-drawn, scaled tile bounds; gallery motion is frozen while the viewer is open. The selected tile is temporarily suppressed while the shared-element morph runs.
- `MediaMorph` interpolates the six rounded polygon vertices into a rectangular image mask, along with the bounds and crop matrix, using the original thumbnail at the tile end and the decoded display-sized image at the viewer end. The same transition runs backwards on close. A Back action during opening reverses the current progress.
- The image is decoded off the UI thread before transitioning. The previous frame stays visible while the next image is decoded. Decodes use a generation guard and cancellation to avoid stale callbacks. Display image resolution is capped at 2400px on the long edge; the original file is not modified.
- Viewer header, back/details controls, filmstrip container and action bar remain mounted when changing media. Only the media layer translates (320ms). The metadata text is updated in place. The filmstrip thumbnails are refreshed for the current index.
- Opening morph 390ms; closing morph 360ms. If a source/destination is not visible (e.g. closing after browsing to another batch), a fade is used instead of animating to an unrelated tile. System reduced-motion and Hive motion settings are respected.
- Video uses TextureView/MediaPlayer, with a still preview retained until the player supplies a frame. The current video frame is captured when possible on leaving playback. Play/pause is explicit.
- MediaStore changes received while the viewer is active are deferred until it closes.

## Icons and popups
- Header and arrow icons use centered ImageButtons rather than compound drawables on empty text buttons. Navigation and viewer actions use centered vector icons, aligned single-line labels and consistent spacing. Video duration badges use font-metric centering.
- Menu, layout selector, search, motion settings, accessible media list, About, and media details use `GlassDialog`: rounded translucent gradient card, border highlight, blurred background when Android supports cross-window blur, dim fallback, styled options, pill buttons and subtle entry animation. Android permission and share panels remain native. Dormant OTA updater code is unchanged.
- Existing Feather-derived SVG and VectorDrawable resources are retained; added sun and sliders assets for menu controls.

## Device verification checklist
- Open media at center, upper edge and either side; check it expands from its own position, preserves aspect ratio and does not turn grey.
- Close immediately during opening; close after viewing; navigate to adjacent visible items and close. For an offscreen destination expect a fade, not a jump to an incorrect tile.
- Tap Next/Previous quickly or swipe the photo: the image should move while header/back button and bottom controls stay fixed. Check a large portrait, landscape and video.
- Start/pause/resume video, navigate away and return, rotate device, and background/foreground the app. Check for stale frames.
- Open all in-app dialogs in dark and light modes; test search keyboard and scrolling settings; check header, nav, action icons and video badges for centering.
- Test continuous and grouped layouts and stationary thumbnails, ensuring V1.1's cache fixes remain.

## Validation and limitations
All eight Java files were parsed using the JDK 17 compiler parser. Android XML resources, SVGs, workflow YAML and drawable references were checked. This is static validation, not Android type checking, APK compilation or real-device testing. Android SDK/Gradle are not installed in the authoring environment; build using the supplied GitHub workflow. Actual animation smoothness, device blur support and video behavior require testing on the phone.
