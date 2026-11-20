package vn.demo.liteload.navigation

import kotlin.test.*

class FeatureLaunchPolicyTest {
    @Test fun missingSplitNeverLaunches() {
        assertEquals(OpenFeatureResult.NotInstalled, launchInstalledFeature(false) { error("must not launch") })
    }
    @Test fun installedSplitLaunchesOnce() {
        var calls = 0
        assertEquals(OpenFeatureResult.Opened, launchInstalledFeature(true) { calls++ })
        assertEquals(1, calls)
    }
    @Test fun launchRuntimeAndSecurityFailuresReturnFailed() {
        assertEquals(OpenFeatureResult.Failed, launchInstalledFeature(true) { throw IllegalStateException("missing Activity") })
        assertEquals(OpenFeatureResult.Failed, launchInstalledFeature(true) { throw SecurityException("denied") })
    }
}
