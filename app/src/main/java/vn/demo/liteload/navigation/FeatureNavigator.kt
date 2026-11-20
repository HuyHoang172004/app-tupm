package vn.demo.liteload.navigation

import android.app.Activity
import android.content.Intent
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import vn.demo.liteload.core.FeatureId

object FeatureNavigator {
    fun open(activity: Activity, featureId: FeatureId): OpenFeatureResult {
        if (activity.isFinishing || activity.isDestroyed) return OpenFeatureResult.Failed
        return try {
            val installed = featureId.moduleName in
                SplitInstallManagerFactory.create(activity.applicationContext).installedModules
            // ActivityNotFoundException and SecurityException are RuntimeExceptions handled by the policy.
            launchInstalledFeature(installed) {
                activity.startActivity(Intent().setClassName(activity.packageName, featureId.activityClassName))
            }
        } catch (_: RuntimeException) { OpenFeatureResult.Failed }
    }
}
