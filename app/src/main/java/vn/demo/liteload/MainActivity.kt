package vn.demo.liteload

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import vn.demo.liteload.core.FeatureId
import vn.demo.liteload.core.FeatureInstallUpdate
import vn.demo.liteload.delivery.PlayFeatureInstaller
import vn.demo.liteload.navigation.FeatureNavigator
import vn.demo.liteload.navigation.OpenFeatureResult
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import vn.demo.liteload.core.InstallUiState

class MainActivity : ComponentActivity() {
    private lateinit var installer: PlayFeatureInstaller
    private val confirmationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        refreshFeatures()
    }
    private fun refreshFeatures() {
        if (::installer.isInitialized) FeatureId.entries.forEach { installer.refresh(it, ::onFeatureUpdate) }
    }
    private fun onFeatureUpdate(update: FeatureInstallUpdate) {
        Log.i("LiteTravelDelivery", "${update.featureId.moduleName}: ${update.state}")
        renderFeatureState(update.featureId.moduleName, update.state)
    }
    private fun featureId(module: String) = FeatureId.entries.first { it.moduleName == module }
    private fun requestInstall(module: String) {
        installer.install(featureId(module), confirmationLauncher, ::onFeatureUpdate)
    }
    private fun requestOpen(module: String) {
        val result = FeatureNavigator.open(this, featureId(module))
        Log.i("LiteTravelDelivery", "OPEN $module: $result")
        when (result) {
            OpenFeatureResult.Opened -> Unit
            OpenFeatureResult.NotInstalled -> installer.refresh(featureId(module), ::onFeatureUpdate)
            OpenFeatureResult.Failed -> renderFeatureState(module, InstallUiState.Failed(-10005, "Không mở được tính năng. Hãy thử lại."))
        }
    }
    override fun onResume() { super.onResume(); refreshFeatures() }
    override fun onDestroy() { if (::installer.isInitialized) installer.close(); super.onDestroy() }
    private fun updateActionAvailability() {
        val active = featureStates.entries.firstOrNull { (_, state) ->
            state is InstallUiState.Pending || state is InstallUiState.Downloading ||
                state is InstallUiState.Installing || state is InstallUiState.RequiresConfirmation
        }?.key
        featureViews.forEach { (module, views) ->
            val state = featureStates[module]
            views.actionButton.isEnabled = when (state) {
                InstallUiState.Installed -> openFeature != null
                InstallUiState.RequiresConfirmation -> module == active && installFeature != null
                InstallUiState.NotInstalled, InstallUiState.Canceled, is InstallUiState.Failed -> active == null && installFeature != null
                else -> false
            }
        }
    }

    private val featureCards = listOf(
        FeatureCardSpec("gallery", R.id.gallery_card, R.string.gallery_title, R.string.gallery_description),
        FeatureCardSpec("audio_guide", R.id.audio_guide_card, R.string.audio_guide_title, R.string.audio_guide_description),
        FeatureCardSpec("itinerary", R.id.itinerary_card, R.string.itinerary_title, R.string.itinerary_description),
        FeatureCardSpec("food", R.id.food_card, R.string.food_title, R.string.food_description)
    )
    private val featureStates = mutableMapOf<String, InstallUiState>()
    private val featureViews = mutableMapOf<String, FeatureCardViews>()
    private var installFeature: ((String) -> Unit)? = null
    private var openFeature: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val optimizationLabel = if (BuildConfig.SIZE_OPTIMIZATION) {
            R.string.optimization_enabled
        } else {
            R.string.optimization_disabled
        }

        findViewById<TextView>(R.id.optimization_status).setText(optimizationLabel)
        findViewById<TextView>(R.id.resource_shrinking_status).setText(optimizationLabel)
        findViewById<TextView>(R.id.version_value).text = BuildConfig.VERSION_NAME
        bindFeatureCards()
        installer = PlayFeatureInstaller(applicationContext)
        connectFeatureActions(::requestInstall, ::requestOpen)
    }

    fun renderFeatureState(moduleName: String, state: InstallUiState) {
        val card = featureViews[moduleName] ?: return
        featureStates[moduleName] = state

        val actionButton = card.actionButton
        card.error.visibility = View.GONE
        card.progress.visibility = View.GONE

        when (state) {
            InstallUiState.NotInstalled -> {
                card.status.setText(R.string.feature_not_installed)
                actionButton.setText(if (installFeature == null) R.string.feature_action_pending else R.string.feature_action_install)
                actionButton.isEnabled = installFeature != null
            }

            InstallUiState.Pending -> {
                card.status.setText(R.string.feature_pending)
                actionButton.isEnabled = false
                actionButton.setText(R.string.feature_action_pending)
            }

            is InstallUiState.Downloading -> {
                card.status.text = getString(R.string.feature_downloading)
                card.progress.visibility = View.VISIBLE
                if (state.totalBytes > 0) {
                    val progress = (state.downloadedBytes.toDouble() / state.totalBytes * 100)
                        .toInt()
                        .coerceIn(0, 100)
                    card.progress.isIndeterminate = false
                    card.progress.progress = progress
                    card.status.text = getString(
                        R.string.feature_progress_format,
                        Formatter.formatFileSize(this, state.downloadedBytes),
                        Formatter.formatFileSize(this, state.totalBytes)
                    )
                } else {
                    card.progress.isIndeterminate = true
                }
                actionButton.isEnabled = false
                actionButton.setText(R.string.feature_downloading)
            }

            InstallUiState.Installing -> {
                card.status.setText(R.string.feature_installing)
                actionButton.isEnabled = false
                actionButton.setText(R.string.feature_installing)
            }

            InstallUiState.Installed -> {
                card.status.setText(R.string.feature_installed)
                actionButton.setText(if (openFeature == null) R.string.feature_action_pending else R.string.feature_action_open)
                actionButton.isEnabled = openFeature != null
            }

            InstallUiState.RequiresConfirmation -> {
                card.status.setText(R.string.feature_confirmation)
                actionButton.isEnabled = false
                actionButton.setText(R.string.feature_action_retry)
            }

            is InstallUiState.Failed -> {
                card.status.setText(R.string.feature_failed)
                card.error.visibility = View.VISIBLE
                card.error.text = state.message
                actionButton.setText(if (installFeature == null) R.string.feature_action_pending else R.string.feature_action_retry)
                actionButton.isEnabled = installFeature != null
            }

            InstallUiState.Canceled -> {
                card.status.setText(R.string.feature_canceled)
                actionButton.setText(if (installFeature == null) R.string.feature_action_pending else R.string.feature_action_retry)
                actionButton.isEnabled = installFeature != null
            }
        }
        updateActionAvailability()
    }

    fun connectFeatureActions(
        install: (String) -> Unit,
        open: (String) -> Unit
    ) {
        installFeature = install
        openFeature = open
        featureStates.forEach { (moduleName, state) ->
            renderFeatureState(moduleName, state)
        }
    }

    private fun bindFeatureCards() {
        featureCards.forEach { spec ->
            val cardRoot = findViewById<View>(spec.cardId)
            val card = FeatureCardViews(
                status = cardRoot.findViewById(R.id.feature_status),
                progress = cardRoot.findViewById(R.id.feature_progress),
                error = cardRoot.findViewById(R.id.feature_error),
                actionButton = cardRoot.findViewById(R.id.feature_action)
            )
            cardRoot.findViewById<TextView>(R.id.feature_title).setText(spec.titleRes)
            cardRoot.findViewById<TextView>(R.id.feature_description).setText(spec.descriptionRes)
            featureViews[spec.moduleName] = card
            featureStates[spec.moduleName] = InstallUiState.NotInstalled

            card.actionButton.setOnClickListener {
                when (featureStates[spec.moduleName]) {
                    InstallUiState.Installed -> openFeature?.invoke(spec.moduleName)
                    else -> installFeature?.invoke(spec.moduleName)
                }
            }
            renderFeatureState(spec.moduleName, InstallUiState.NotInstalled)
        }
    }
}

private data class FeatureCardSpec(
    val moduleName: String,
    val cardId: Int,
    val titleRes: Int,
    val descriptionRes: Int
)

private data class FeatureCardViews(
    val status: TextView,
    val progress: ProgressBar,
    val error: TextView,
    val actionButton: Button
)
