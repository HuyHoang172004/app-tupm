package vn.demo.liteload.delivery

import kotlin.test.*
import vn.demo.liteload.core.*

class InstallCoordinatorTest {
    private class Backend : InstallBackend {
        val installed = mutableSetOf<String>()
        var states = listOf<DeliverySession>()
        var listener: ((DeliverySession) -> Unit)? = null
        var starts = 0; var registrations = 0; var removals = 0
        var holdStart = false; var holdQuery = false
        var startSuccess: ((Int) -> Unit)? = null
        var startFailure: ((DeliveryError) -> Unit)? = null
        var querySuccess: ((List<DeliverySession>) -> Unit)? = null
        var queryFailure: ((DeliveryError) -> Unit)? = null
        var queryError: DeliveryError? = null
        var throwStart = false
        override fun installedModules(): Set<String> = installed
        override fun sessions(success: (List<DeliverySession>) -> Unit, failure: (DeliveryError) -> Unit) {
            if (holdQuery) { querySuccess = success; queryFailure = failure }
            else queryError?.let(failure) ?: success(states)
        }
        override fun start(feature: FeatureId, success: (Int) -> Unit, failure: (DeliveryError) -> Unit) {
            starts++
            if (throwStart) throw IllegalStateException("synchronous failure")
            startSuccess = success; startFailure = failure
            if (!holdStart) success(7)
        }
        override fun listen(listener: (DeliverySession) -> Unit) { registrations++; this.listener = listener }
        override fun unlisten() { removals++; listener = null }
        fun emit(state: DeliverySession) {
            states = states.filterNot { it.id == state.id } + state
            if (state.status == DeliveryStatus.INSTALLED) installed.addAll(state.modules)
            listener?.invoke(state)
        }
    }
    private val backend = Backend()
    private val coordinator = InstallCoordinator(backend) { it() }
    private val updates = mutableListOf<FeatureInstallUpdate>()
    private fun session(status: DeliveryStatus, id: Int = 7, module: String = "gallery",
                        downloaded: Long = 13, total: Long = 100, error: Int = 0) =
        DeliverySession(id, setOf(module), status, downloaded, total, error)
    private fun install(confirm: (Int) -> Boolean = { true }) = coordinator.install(FeatureId.GALLERY, confirm, updates::add)

    @Test fun installedComesFromBackendAndSkipsNewRequest() {
        backend.installed += "gallery"; install()
        assertTrue(coordinator.isInstalled(FeatureId.GALLERY))
        assertEquals(InstallUiState.Installed, updates.single().state)
        assertEquals(0, backend.starts)
    }
    @Test fun refreshMissingFeatureReleasesListener() {
        coordinator.refresh(FeatureId.FOOD, updates::add)
        assertEquals(FeatureInstallUpdate(FeatureId.FOOD, InstallUiState.NotInstalled), updates.single())
        assertNull(backend.listener)
    }
    @Test fun rejectsDuplicateAndOtherFeatureWithoutReplacingOriginalCallback() {
        install(); val rejected = mutableListOf<FeatureInstallUpdate>()
        coordinator.install(FeatureId.GALLERY, { true }, rejected::add)
        coordinator.install(FeatureId.FOOD, { true }, rejected::add)
        assertEquals(1, backend.starts); assertEquals(2, rejected.size)
        assertTrue(rejected.all { (it.state as InstallUiState.Failed).code == -10001 })
        backend.emit(session(DeliveryStatus.DOWNLOADING))
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
    }
    @Test fun ignoresUnrelatedIdsAndWrongModuleEvenWithMatchingId() {
        install(); backend.emit(session(DeliveryStatus.DOWNLOADING, id = 99))
        backend.emit(session(DeliveryStatus.INSTALLED, module = "food"))
        assertEquals(listOf(InstallUiState.Pending), updates.map { it.state })
    }
    @Test fun preservesUnknownTotalAndRealByteCounts() {
        install(); backend.emit(session(DeliveryStatus.DOWNLOADING, downloaded = 1234, total = 0))
        assertEquals(InstallUiState.Downloading(1234, 0), updates.last().state)
    }
    @Test fun terminalInstalledUnregistersAndAllowsAnotherFeature() {
        install(); backend.emit(session(DeliveryStatus.INSTALLED))
        assertNull(backend.listener); assertEquals(1, backend.removals)
        coordinator.install(FeatureId.FOOD, { true }, updates::add)
        assertEquals(2, backend.starts)
    }
    @Test fun errorAndCancellationMapExactlyAndPermitRetry() {
        install(); backend.emit(session(DeliveryStatus.FAILED, error = -6))
        assertEquals(-6, (updates.last().state as InstallUiState.Failed).code)
        install(); backend.emit(session(DeliveryStatus.CANCELED))
        assertEquals(InstallUiState.Canceled, updates.last().state)
        install(); assertEquals(3, backend.starts)
    }
    @Test fun synchronousFailureIsReportedAndCleanedUp() {
        backend.throwStart = true; install()
        assertTrue(updates.last().state is InstallUiState.Failed); assertNull(backend.listener)
    }
    @Test fun asynchronousFailureKeepsPlayErrorCode() {
        backend.holdStart = true; install()
        backend.startFailure!!(DeliveryError(-10, "store unavailable"))
        assertEquals(InstallUiState.Failed(-10, "store unavailable"), updates.last().state)
        assertNull(backend.listener)
    }
    @Test fun queryFailureDoesNotAttemptInstallation() {
        backend.queryError = DeliveryError(-3, "API unavailable"); install()
        assertEquals(0, backend.starts); assertEquals(-3, (updates.last().state as InstallUiState.Failed).code)
        assertNull(backend.listener)
    }
    @Test fun listenerEventBeforeStartTaskSuccessIsNotLost() {
        backend.holdStart = true; install()
        backend.emit(session(DeliveryStatus.DOWNLOADING))
        backend.startSuccess!!(7)
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
    }
    @Test fun restoresActiveSessionWithoutStartingADuplicateAfterRecreation() {
        install(); backend.emit(session(DeliveryStatus.DOWNLOADING)); coordinator.close()
        val recreated = InstallCoordinator(backend) { it() }
        recreated.refresh(FeatureId.GALLERY, updates::add)
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
        recreated.install(FeatureId.GALLERY, { true }, updates::add)
        assertEquals(1, backend.starts)
        backend.emit(session(DeliveryStatus.INSTALLED)); assertEquals(InstallUiState.Installed, updates.last().state)
        recreated.close()
    }
    @Test fun systemSessionForAnotherModuleBlocksStartEvenOnFreshInstance() {
        backend.states = listOf(session(DeliveryStatus.DOWNLOADING, module = "food")); install()
        assertEquals(0, backend.starts); assertEquals(-10001, (updates.last().state as InstallUiState.Failed).code)
    }
    @Test fun confirmationIsLaunchedOnceAndNeedsExplicitRetryAfterRefresh() {
        var launches = 0; val confirm: (Int) -> Boolean = { assertEquals(7, it); launches++; true }
        install(confirm); backend.emit(session(DeliveryStatus.CONFIRMATION)); backend.emit(session(DeliveryStatus.CONFIRMATION))
        assertEquals(1, launches); assertEquals(InstallUiState.RequiresConfirmation, updates.last().state)
        coordinator.refresh(FeatureId.GALLERY, updates::add)
        assertEquals(1, launches) // Refresh never fabricates a user confirmation.
        install(confirm); assertEquals(2, launches)
        backend.emit(session(DeliveryStatus.CANCELED)); assertNull(backend.listener)
    }
    @Test fun confirmationFalseAndExceptionProduceRecoverableFailure() {
        install { false }; backend.emit(session(DeliveryStatus.CONFIRMATION))
        assertEquals(-10003, (updates.last().state as InstallUiState.Failed).code)
        install { throw IllegalStateException("no Activity") }
        assertEquals(-10003, (updates.last().state as InstallUiState.Failed).code)
        assertEquals(1, backend.starts) // Existing confirmable session reused.
    }
    @Test fun refreshRestoresConfirmationWithoutKeepingOldLauncher() {
        backend.states = listOf(session(DeliveryStatus.CONFIRMATION))
        coordinator.refresh(FeatureId.GALLERY, updates::add)
        assertEquals(InstallUiState.RequiresConfirmation, updates.last().state)
        var launched = false; install { launched = true; true }
        assertTrue(launched); assertEquals(0, backend.starts)
    }
    @Test fun staleRefreshCannotOverwriteNewerListenerProgress() {
        install(); backend.holdQuery = true
        coordinator.refresh(FeatureId.GALLERY, updates::add)
        backend.emit(session(DeliveryStatus.DOWNLOADING))
        backend.querySuccess!!(listOf(session(DeliveryStatus.PENDING)))
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
    }
    @Test fun oldTerminalSessionCannotTerminateANewPendingStart() {
        backend.states = listOf(session(DeliveryStatus.FAILED, id = 3, error = -6))
        backend.holdStart = true
        install()
        coordinator.refresh(FeatureId.GALLERY, updates::add)
        assertEquals(InstallUiState.Pending, updates.last().state)
        backend.startSuccess!!(7)
        backend.emit(session(DeliveryStatus.DOWNLOADING))
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
    }
    @Test fun staleRefreshFailureCannotOverwriteNewerListenerState() {
        install(); backend.holdQuery = true
        coordinator.refresh(FeatureId.GALLERY, updates::add)
        backend.emit(session(DeliveryStatus.DOWNLOADING))
        backend.queryFailure!!(DeliveryError(-3, "old query failed"))
        assertEquals(InstallUiState.Downloading(13, 100), updates.last().state)
    }
    @Test fun closeUnregistersAndSuppressesDelayedTaskResults() {
        backend.holdStart = true; install(); coordinator.close()
        val before = updates.size; backend.startSuccess!!(7)
        backend.startFailure!!(DeliveryError(-1, "late"))
        assertEquals(before, updates.size); assertNull(backend.listener)
        coordinator.close(); assertEquals(1, backend.removals)
    }
    @Test fun dispatchSerializesCallbacksAndCloseSuppressesQueuedEvents() {
        val queue = ArrayDeque<() -> Unit>()
        val engine = InstallCoordinator(backend) { queue.addLast(it) }
        fun flush() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
        engine.install(FeatureId.GALLERY, { true }, updates::add)
        assertTrue(updates.isEmpty()); flush(); assertEquals(InstallUiState.Pending, updates.last().state)
        backend.emit(session(DeliveryStatus.DOWNLOADING)); engine.close(); flush()
        assertEquals(1, updates.size); assertNull(backend.listener)
    }
    @Test fun allPlayStatusesHaveDefinedMappings() {
        assertEquals(InstallUiState.Pending, session(DeliveryStatus.PENDING).uiState())
        assertEquals(InstallUiState.Installing, session(DeliveryStatus.CANCELING).uiState())
        assertEquals(InstallUiState.Installing, session(DeliveryStatus.INSTALLING).uiState())
        assertTrue(session(DeliveryStatus.UNKNOWN).uiState() is InstallUiState.Failed)
    }
}
