# APRIL Build Status Ledger

Updated: 2026-10-29

## Current baseline
- Repository: `nastynate2683-tech/April`
- Branch: `floatdeck-shell-integration`
- Latest known branch commit before this ledger: `2d0819f796f3506f61087183e2e7c33974efa86f` — "Verify approved APRIL icon is packaged"
- Current icon asset: `integration-patch/ic_april_photo.png`
- Launcher foreground: `integration-patch/ic_launcher_foreground.xml` points to `@drawable/ic_april_photo`
- Launcher adaptive icon: `integration-patch/ic_launcher.xml`
- Manifest launcher icon: `@mipmap/ic_launcher`

## Build workflow
- Workflow: `.github/workflows/build-apk-final.yml`
- Trigger: push to `floatdeck-shell-integration` or manual dispatch.
- The workflow copies the approved icon into the Android drawable resources and packages it into the launcher foreground.
- APK verification checks the APK archive includes the APRIL photo icon and checks expected app strings.

## Status (do not overstate)
- Icon integration is present in source and launcher foreground configuration.
- A new build is expected from the latest branch push; build success, actual APK icon rendering, runtime behavior, and successful user download must be verified from the resulting run/artifact before marking complete.
- Do not modify or redesign the approved 3D artwork as part of APK/build fixes.
- Do not mark a build ready based only on a filename or commit; record the exact run number, commit SHA, artifact ID/name, size, and test outcome when verified.

## Next acceptance checks
1. Find the workflow run for the latest commit and confirm it completed successfully.
2. Confirm its artifact belongs to that exact commit.
3. Download the artifact and inspect its contents.
4. Confirm the packaged launcher resource is the approved icon.
5. Confirm duplicate-download regression check and expected renderer/app markers pass.
6. Send only the verified artifact link; record any unresolved device-side checks separately.
