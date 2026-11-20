package vn.demo.liteload.delivery

import vn.demo.liteload.core.FeatureId
import vn.demo.liteload.core.FeatureInstallUpdate
import vn.demo.liteload.core.InstallUiState

internal enum class DeliveryStatus {
    PENDING, DOWNLOADING, INSTALLING, INSTALLED, CONFIRMATION, FAILED, CANCELED, CANCELING, UNKNOWN;
    val terminal: Boolean get() = this == INSTALLED || this == FAILED || this == CANCELED
}

internal data class DeliverySession(
    val id: Int, val modules: Set<String>, val status: DeliveryStatus,
    val downloaded: Long = 0, val total: Long = 0, val error: Int = 0
) {
    fun uiState(): InstallUiState = when (status) {
        DeliveryStatus.PENDING -> InstallUiState.Pending
        DeliveryStatus.DOWNLOADING -> InstallUiState.Downloading(downloaded, total)
        DeliveryStatus.INSTALLING, DeliveryStatus.CANCELING -> InstallUiState.Installing
        DeliveryStatus.INSTALLED -> InstallUiState.Installed
        DeliveryStatus.CONFIRMATION -> InstallUiState.RequiresConfirmation
        DeliveryStatus.FAILED -> InstallUiState.Failed(error, "Play không thể cài tính năng (mã $error). Hãy thử lại.")
        DeliveryStatus.CANCELED -> InstallUiState.Canceled
        DeliveryStatus.UNKNOWN -> InstallUiState.Failed(-10004, "Trạng thái cài đặt chưa được hỗ trợ.")
    }
}

internal data class DeliveryError(val code: Int, val message: String)
internal interface InstallBackend {
    fun installedModules(): Set<String>
    fun sessions(success: (List<DeliverySession>) -> Unit, failure: (DeliveryError) -> Unit)
    fun start(feature: FeatureId, success: (Int) -> Unit, failure: (DeliveryError) -> Unit)
    fun listen(listener: (DeliverySession) -> Unit)
    fun unlisten()
}

/** Serialized on dispatch (the Android main looper). System sessions, not memory, decide retries. */
internal class InstallCoordinator(
    private val backend: InstallBackend,
    private val dispatch: (() -> Unit) -> Unit
) : AutoCloseable {
    private data class Request(val feature: FeatureId, val token: Long,
        var confirm: ((Int) -> Boolean)?, var sessionId: Int? = null,
        var starting: Boolean = false, var confirmationInFlight: Boolean = false)
    @Volatile private var closed = false
    private var request: Request? = null
    private var nextToken = 0L
    private var queries = 0
    private var listening = false
    private val callbacks = mutableMapOf<FeatureId, (FeatureInstallUpdate) -> Unit>()
    private val observed = mutableMapOf<FeatureId, DeliverySession>()
    private val revisions = mutableMapOf<FeatureId, Long>()
    private val earlyStates = mutableMapOf<Int, DeliverySession>()

    fun isInstalled(feature: FeatureId): Boolean = !closed && feature.moduleName in backend.installedModules()

    fun refresh(feature: FeatureId, callback: (FeatureInstallUpdate) -> Unit) = dispatch {
        if (!closed) {
            callbacks[feature] = callback
            try {
                if (isInstalled(feature)) finish(feature, InstallUiState.Installed)
                else {
                    val revision = revisions[feature] ?: 0
                    query({ states ->
                        if ((revisions[feature] ?: 0) == revision) {
                            val current = request?.takeIf { it.feature == feature }
                            val candidates = states.filter { feature.moduleName in it.modules }
                            val state = if (current?.sessionId != null) {
                                candidates.firstOrNull { it.id == current.sessionId }
                            } else {
                                candidates.filter { !it.status.terminal }.maxByOrNull { it.id }
                                    ?: if (current == null) candidates.maxByOrNull { it.id } else null
                            }
                            if (state != null) {
                                if (state.status == DeliveryStatus.CONFIRMATION) {
                                    request?.takeIf { it.feature == feature }?.confirmationInFlight = false
                                }
                                apply(feature, state, allowConfirmation = false)
                            } else if (request?.feature == feature) emit(feature, observed[feature]?.uiState() ?: InstallUiState.Pending)
                            else finish(feature, InstallUiState.NotInstalled)
                        }
                    }, { error ->
                        if ((revisions[feature] ?: 0) == revision) fail(feature, error)
                    })
                }
            } catch (e: Exception) { fail(feature, exceptionError(e)) }
        }
    }

    fun install(feature: FeatureId, confirm: (Int) -> Boolean,
                callback: (FeatureInstallUpdate) -> Unit) = dispatch {
        if (!closed) {
            val current = request
            if (current != null) {
                val state = observed[feature]
                if (current.feature == feature && state?.status == DeliveryStatus.CONFIRMATION &&
                    !current.confirmationInFlight) {
                    callbacks[feature] = callback
                    current.confirm = confirm
                    launchConfirmation(feature, state.id)
                } else callback(FeatureInstallUpdate(feature, busyError().let { InstallUiState.Failed(it.code, it.message) }))
            } else {
                revisions[feature] = (revisions[feature] ?: 0) + 1
                callbacks[feature] = callback
                try {
                    if (isInstalled(feature)) finish(feature, InstallUiState.Installed)
                    else {
                        val token = ++nextToken
                        request = Request(feature, token, confirm)
                        emit(feature, InstallUiState.Pending)
                        query({ states ->
                            if (request?.token == token) {
                                val active = states.filter { !it.status.terminal && it.status != DeliveryStatus.UNKNOWN }
                                if (active.any { feature.moduleName !in it.modules }) fail(feature, busyError())
                                else {
                                    val existing = active.filter { feature.moduleName in it.modules }.maxByOrNull { it.id }
                                    if (existing != null) {
                                        request?.sessionId = existing.id
                                        apply(feature, existing)
                                    } else start(feature, token)
                                }
                            }
                        }, { error -> if (request?.token == token) fail(feature, error) })
                    }
                } catch (e: Exception) { fail(feature, exceptionError(e)) }
            }
        }
    }

    private fun start(feature: FeatureId, token: Long) {
        if (closed || request?.token != token) return
        request?.starting = true
        try {
            backend.start(feature, { id -> dispatch {
                if (!closed && request?.token == token) {
                    try {
                    request?.starting = false
                    request?.sessionId = id
                    if (isInstalled(feature)) finish(feature, InstallUiState.Installed)
                    else if (id <= 0) fail(feature, DeliveryError(-10002, "Play không trả về phiên cài đặt hợp lệ."))
                    else {
                        val early = earlyStates[id]
                        earlyStates.clear()
                        if (early != null) apply(feature, early)
                        else observed[feature] = DeliverySession(id, setOf(feature.moduleName), DeliveryStatus.PENDING)
                    }
                    } catch (e: Exception) { fail(feature, exceptionError(e)) }
                }
            } }, { error -> dispatch { if (!closed && request?.token == token) fail(feature, error) } })
        } catch (e: Exception) { fail(feature, exceptionError(e)) }
    }

    private fun query(success: (List<DeliverySession>) -> Unit, failure: (DeliveryError) -> Unit) {
        queries++
        try {
            ensureListener()
            backend.sessions({ states -> dispatch {
                queries--
                if (!closed) success(states)
                releaseListenerIfIdle()
            } }, { error -> dispatch {
                queries--
                if (!closed) failure(error)
                releaseListenerIfIdle()
            } })
        } catch (e: Exception) {
            queries--
            failure(exceptionError(e))
            releaseListenerIfIdle()
        }
    }

    private fun ensureListener() {
        if (!listening) {
            backend.listen { state -> dispatch {
                if (!closed) {
                    val current = request
                    if (current?.starting == true && current.feature.moduleName in state.modules) {
                        earlyStates[state.id] = state
                    }
                    val targets = observed.filter { (feature, session) ->
                        session.id == state.id && feature.moduleName in state.modules
                    }.keys.toList()
                    targets.forEach { apply(it, state) }
                }
            } }
            listening = true
        }
    }

    private fun apply(feature: FeatureId, state: DeliverySession, allowConfirmation: Boolean = true) {
        if (closed || feature.moduleName !in state.modules) return
        revisions[feature] = (revisions[feature] ?: 0) + 1
        if (state.status.terminal || state.status == DeliveryStatus.UNKNOWN) finish(feature, state.uiState())
        else {
            observed[feature] = state
            emit(feature, state.uiState())
            if (!closed && state.status == DeliveryStatus.CONFIRMATION && allowConfirmation)
                launchConfirmation(feature, state.id)
        }
    }

    private fun launchConfirmation(feature: FeatureId, id: Int) {
        val current = request?.takeIf { it.feature == feature && it.sessionId == id } ?: return
        val confirm = current.confirm ?: return
        if (current.confirmationInFlight || closed) return
        current.confirmationInFlight = true
        try {
            if (!confirm(id)) fail(feature, DeliveryError(-10003, "Không mở được xác nhận của Play. Hãy thử lại."))
        } catch (e: Exception) { fail(feature, DeliveryError(-10003, e.message ?: "Không mở được xác nhận của Play.")) }
    }

    private fun emit(feature: FeatureId, state: InstallUiState) {
        if (!closed) callbacks[feature]?.invoke(FeatureInstallUpdate(feature, state))
    }

    private fun finish(feature: FeatureId, state: InstallUiState) {
        val callback = callbacks.remove(feature)
        observed.remove(feature)
        if (request?.feature == feature) {
            request?.confirm = null
            request = null
            earlyStates.clear()
        }
        releaseListenerIfIdle()
        if (!closed) callback?.invoke(FeatureInstallUpdate(feature, state))
    }

    private fun fail(feature: FeatureId, error: DeliveryError) = finish(feature, InstallUiState.Failed(error.code, error.message))
    private fun releaseListenerIfIdle() {
        if (listening && (closed || (queries == 0 && request == null && observed.isEmpty()))) {
            listening = false
            backend.unlisten()
        }
    }
    override fun close() {
        closed = true // Suppress already queued callbacks immediately, even from a worker thread.
        dispatch {
            request?.confirm = null
            request = null
            callbacks.clear()
            observed.clear()
            earlyStates.clear()
            releaseListenerIfIdle()
        }
    }
    private fun busyError() = DeliveryError(-10001, "Đang có phiên cài đặt. Hãy chờ phiên hiện tại kết thúc.")
    private fun exceptionError(e: Exception) = DeliveryError(-10002, e.message ?: "Không truy vấn được Play Feature Delivery.")
}
