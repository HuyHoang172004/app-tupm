package vn.demo.liteload.delivery

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import vn.demo.liteload.core.FeatureId
import vn.demo.liteload.core.FeatureInstallUpdate

/** Activity-scoped facade; the Play manager retains only applicationContext. Close in onDestroy. */
class PlayFeatureInstaller(context: Context) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val backend = PlayInstallBackend(SplitInstallManagerFactory.create(context.applicationContext))
    private val coordinator = InstallCoordinator(backend) { work ->
        if (Looper.myLooper() == Looper.getMainLooper()) work() else handler.post { work() }
    }

    fun isInstalled(featureId: FeatureId): Boolean = try {
        coordinator.isInstalled(featureId)
    } catch (_: RuntimeException) { false }

    fun refresh(featureId: FeatureId, onUpdate: (FeatureInstallUpdate) -> Unit) =
        coordinator.refresh(featureId, onUpdate)

    fun install(featureId: FeatureId, confirmationLauncher: ActivityResultLauncher<IntentSenderRequest>,
                onUpdate: (FeatureInstallUpdate) -> Unit) =
        coordinator.install(featureId, { sessionId -> backend.confirm(sessionId, confirmationLauncher) }, onUpdate)

    override fun close() = coordinator.close()
}

internal class PlayInstallBackend(private val manager: SplitInstallManager) : InstallBackend {
    private val confirmationStates = mutableMapOf<Int, SplitInstallSessionState>()
    private var playListener: SplitInstallStateUpdatedListener? = null
    override fun installedModules(): Set<String> = manager.installedModules

    override fun sessions(success: (List<DeliverySession>) -> Unit, failure: (DeliveryError) -> Unit) {
        manager.sessionStates.addOnSuccessListener { states -> success(states.map(::snapshot)) }
            .addOnFailureListener { failure(error(it)) }
    }
    override fun start(feature: FeatureId, success: (Int) -> Unit, failure: (DeliveryError) -> Unit) {
        manager.startInstall(SplitInstallRequest.newBuilder().addModule(feature.moduleName).build())
            .addOnSuccessListener { success(it) }.addOnFailureListener { failure(error(it)) }
    }
    override fun listen(listener: (DeliverySession) -> Unit) {
        check(playListener == null)
        val registered = SplitInstallStateUpdatedListener { listener(snapshot(it)) }
        manager.registerListener(registered)
        playListener = registered
    }
    override fun unlisten() {
        val listener = playListener
        playListener = null
        confirmationStates.clear()
        if (listener != null) manager.unregisterListener(listener)
    }
    fun confirm(id: Int, launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val state = confirmationStates[id] ?: return false
        return manager.startConfirmationDialogForResult(state, launcher)
    }
    private fun snapshot(state: SplitInstallSessionState): DeliverySession {
        val status = when (state.status()) {
            SplitInstallSessionStatus.PENDING -> DeliveryStatus.PENDING
            SplitInstallSessionStatus.DOWNLOADING -> DeliveryStatus.DOWNLOADING
            SplitInstallSessionStatus.DOWNLOADED, SplitInstallSessionStatus.INSTALLING -> DeliveryStatus.INSTALLING
            SplitInstallSessionStatus.INSTALLED -> DeliveryStatus.INSTALLED
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> DeliveryStatus.CONFIRMATION
            SplitInstallSessionStatus.FAILED -> DeliveryStatus.FAILED
            SplitInstallSessionStatus.CANCELED -> DeliveryStatus.CANCELED
            SplitInstallSessionStatus.CANCELING -> DeliveryStatus.CANCELING
            else -> DeliveryStatus.UNKNOWN
        }
        if (status == DeliveryStatus.CONFIRMATION) confirmationStates[state.sessionId()] = state
        else confirmationStates.remove(state.sessionId())
        return DeliverySession(state.sessionId(), state.moduleNames().toSet(), status,
            state.bytesDownloaded(), state.totalBytesToDownload(), state.errorCode())
    }
    private fun error(e: Exception) = DeliveryError((e as? SplitInstallException)?.errorCode ?: -10002,
        e.message ?: "Play Feature Delivery không khả dụng.")
}
