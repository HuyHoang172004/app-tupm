# B+C integration handoff

Based on origin/main `9fa490bf9b85eddee6e72138c10fc025c015053f`.
B's Home layout, renderFeatureState/connectFeatureActions and R8 switch are
preserved. MainActivity now connects the real C installer/navigator, registers
Activity Result confirmation, refreshes each feature on resume and closes on
destroy. Other download actions are disabled while a session is active.

The four on-demand modules use the existing contract identifiers. Base uses
SplitCompatApplication; feature Activities install SplitCompat. Dependencies are
Play Feature Delivery 2.1.0, Activity KTX 1.10.1, with a Fragment 1.8.6 constraint
needed by Activity Result. No wrapper or keep rules were changed.

## Executed validation

- Publication checks: 28 JVM tests (3 contract + 25 delivery/navigation) and
  6 asset checks passed. An initial offline test attempt lacked a cached plugin
  dependency; the online retry completed successfully.

- Production release `:app:bundleRelease -PsizeOptimization=true`: PASS,
  206 tasks. Targeted Kotlin compile tasks for base and all four splits passed.
- Production Home, API 35 read-only AVD, bundletool 1.18.1 local-testing:
  base-only install; all four initially NotInstalled; install/open all four;
  process restart, installed states retained and all four reopened: PASS.
- The smoke automation initially included an unrelated Nexus Launcher fatal
  crash in its global log assertion. Saved evidence was rechecked with an
  application-specific filter and passed. This was not a LiteTravel crash.
- These release/device results precede publication-only documentation and
  portable test-harness additions; production functionality is unchanged.

Large outputs remain local in the original workspace `.gradle/bc-results/`:
release.log, smoke-resume.log, production-logcat.txt, final-check.log,
production.apks. The AAB is app/build/outputs/bundle/release/app-release.aab
in this integration worktree. They are intentionally not committed.

This proves bundletool local delivery, not Google Play network distribution.
Real Play confirmation/cancellation, physical-device testing and human audio
listening were not executed in this production smoke run. No size experiments
were performed. See ASSET_PROVENANCE.md for original vectors and narration.

## Reproduce

Use an existing JDK 17, Android SDK 36 / Build Tools 35.0.0 and Gradle 8.13.
The known local setup used Unity's Temurin 17 and a cached Gradle executable;
no machine-specific paths are stored in project configuration.

```powershell
& $gradle -p core/contracts/verification :core:contracts:test :delivery-tests:test
python core/contracts/verification/check_assets.py
& $gradle :app:bundleRelease '-PsizeOptimization=true'
# bundletool: build-apks --local-testing, then install-apks --modules=base
```

The portable JVM test build compiles the actual coordinator/navigation policy
source; fake backends are confined to tests. It does not add a validation Home
or change the production module graph.

## Review gate

B_C_SHARED_WORKFLOW_AND_API.md section 5 permits a separate contract PR or a
contract-first commit in the implementation PR; section 6 calls for the separate
contract PR to merge first. No B approval is inferred. A combined PR may be
presented for B's review, with contract approval explicitly required before
integration acceptance. B should resolve the review-order exception or require
the separate contract PR before merging. Identifiers/signatures are unchanged
from the documented workflow. Nothing is pushed or merged automatically.
