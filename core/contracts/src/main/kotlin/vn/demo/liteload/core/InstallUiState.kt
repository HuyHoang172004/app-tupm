package vn.demo.liteload.core

sealed interface InstallUiState {
    data object NotInstalled : InstallUiState
    data object Pending : InstallUiState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : InstallUiState
    data object Installing : InstallUiState
    data object Installed : InstallUiState
    data object RequiresConfirmation : InstallUiState
    data class Failed(val code: Int, val message: String) : InstallUiState
    data object Canceled : InstallUiState
}
