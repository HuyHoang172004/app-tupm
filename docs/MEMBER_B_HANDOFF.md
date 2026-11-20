# Member B handoff to D

Integration baseline: local commit `de8aa01` (production Home + four on-demand
features, release R8 ON and API 35 local-testing already passed). Subsequent B
changes update Home text to match the existing Hội An content and add isolated
experiment tooling. No delivery, navigation, layout or feature implementation
changes are needed. See BC_INTEGRATION.md for the earlier device evidence.

Home state handling was reviewed: Failed displays the actual message and retry;
Canceled permits retry; Downloading uses actual byte counts and an indeterminate
bar for total <= 0; Installed enables Open; other downloads are locked while an
installation is active. These bindings are unchanged from the verified baseline.
No new error/cancellation/confirmation scenario was exercised on a device in
this B pass. No expensive C device suite is repeated for text-only changes.

## Reproducible conditions (D performs measurements)

| Case | sizeOptimization | All four splits | Purpose |
|---|---|---|---|
| E0 | false | install-time | Unoptimized baseline |
| E1 | true | install-time | Optimization comparison against E0 |
| E2 | true | on-demand | Delivery comparison against E1 |
| E2_OFF | false | on-demand | B release ON/OFF verification only, **not E0** |

Commit the final source before D's measurement run, then from the repository:

```powershell
$run = python tools/prepare_experiments.py
# Set existing tools/environment explicitly; no automatic installation:
$gradle = '<existing Gradle 8.13>/bin/gradle.bat'
$env:JAVA_HOME = '<existing JDK 17>'
$env:ANDROID_HOME = '<existing Android SDK>'
foreach ($case in @('E0','E1','E2')) {
    & tools/Build-Experiment.ps1 -Case $case -RunDirectory $run -GradleExecutable $gradle
}
```

The preparer refuses dirty source unless --allow-dirty is explicitly supplied
for labelled readiness checks. It freezes tracked/non-ignored source, records
base commit, dirty status, every file hash and an overall fingerprint. E0/E1
source bytes must be identical; E1/E2 may differ only in the four manifest
delivery tags. E2_OFF/E2 source bytes are identical. It never edits the original
on-demand manifests. Each build refuses modified snapshot files or an existing
artifact destination; logs/results and distinct CASE.aab files stay ignored in
.gradle/b-experiments/<run>/artifacts. No reset, clean or overwrite is needed.

Use one JDK/Gradle/SDK toolchain, identical signing credentials and the SAME
actual device-spec.json for all conditions. Release signing configuration is
unchanged; if bundles are unsigned, supply the same explicit bundletool APK
signing key/alias/password externally for every condition. Never commit keys.
Keep the run's snapshot.json and CASE-result.json with the actual artifacts.

D then validates AABs, builds device-specific APK sets and collects measurements
using the established shared-core procedure. Use default initial modules for
initial delivery and all modules for the fully installed comparison; do not use
universal APK size as initial download size. Do not compare on-demand R8 OFF to
E2 and call that E0→E1. No results.csv values or reduction percentages are
provided here. Real Google Play distribution remains outside local-testing.

## Current validation

- PASS: baseline integration commit and clean worktree verified before B edits.
- PASS: prepared snapshot comparison invariants (recorded in snapshot.json).
- PASS: B release R8 OFF (E2_OFF), 199 tasks, exit 0; ON (E2), 206 tasks,
  exit 0. Both include lintVitalRelease. BuildConfig.SIZE_OPTIMIZATION is
  false/true respectively, and minifyReleaseWithR8 ran only for ON.
- PASS: both new AABs retain all four manifest/DEX Activity entry points and
  keep narration inside audio_guide. Six resource/media checks passed.
- Readiness output: `.gradle/b-experiments/20261120-084903-9eba3d4c/artifacts/`
  contains E2_OFF.aab, E2.aab, separate logs and result JSONs. Source fingerprint:
  `9eba3d4cc4b7698c00938aa6f4e3fc7d9ada6ae918daa2686b1d9e167d8ea92d`.
  Both builds used that SAME frozen source. The snapshot is explicitly dirty,
  based on de8aa01; it is not presented as a new committed source revision.
- FAILED: none of the requested release or asset checks in this B pass.
- The final handoff uses a NEW clean-commit snapshot, with E0/E1/E2 AABs,
  source bundle, per-case build results and hashes packaged locally for D.
  See the package README and snapshot.json for its exact commit and run results;
  do not relabel the earlier dirty readiness artifacts as committed builds.
- Signing credentials and an actual shared device spec remain D's run inputs.
- NOT RUN by B: device measurements, physical-device/Play testing.
- NOT RUN: repeat C on-demand device smoke; B changes do not affect that flow.

Readiness runs with uncommitted B changes must be labelled dirty and must not be
reported as a committed experimental snapshot. The packaged clean snapshot
supersedes the earlier readiness run for D. Build success is not measurement.
