package vn.demo.liteload.core

/** Delivered on the Android main thread. A zero total means indeterminate progress. */
data class FeatureInstallUpdate(val featureId: FeatureId, val state: InstallUiState)
