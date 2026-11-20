package vn.demo.liteload.navigation

enum class OpenFeatureResult { Opened, NotInstalled, Failed }

internal fun launchInstalledFeature(installed: Boolean, launch: () -> Unit): OpenFeatureResult {
    if (!installed) return OpenFeatureResult.NotInstalled
    return try {
        launch()
        OpenFeatureResult.Opened
    } catch (_: RuntimeException) { OpenFeatureResult.Failed }
}
