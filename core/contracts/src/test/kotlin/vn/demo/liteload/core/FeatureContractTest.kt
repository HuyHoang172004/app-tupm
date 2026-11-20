package vn.demo.liteload.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureContractTest {
    @Test fun identifiersMatchTheApprovedWorkflow() {
        val expected = mapOf(
            FeatureId.GALLERY to ("gallery" to "vn.demo.liteload.feature.gallery.GalleryActivity"),
            FeatureId.AUDIO_GUIDE to ("audio_guide" to "vn.demo.liteload.feature.audioguide.AudioGuideActivity"),
            FeatureId.ITINERARY to ("itinerary" to "vn.demo.liteload.feature.itinerary.ItineraryActivity"),
            FeatureId.FOOD to ("food" to "vn.demo.liteload.feature.food.FoodActivity")
        )
        assertEquals(4, FeatureId.entries.size)
        assertEquals(FeatureContract.MODULES, FeatureId.entries.map { it.moduleName }.toSet())
        expected.forEach { (id, names) ->
            assertEquals(names.first, id.moduleName)
            assertEquals(names.second, id.activityClassName)
        }
    }

    @Test fun eventsPreserveFeatureAndActualBytesIncludingUnknownTotal() {
        val event = FeatureInstallUpdate(FeatureId.FOOD, InstallUiState.Downloading(125, 0))
        assertEquals(FeatureId.FOOD, event.featureId)
        assertEquals(InstallUiState.Downloading(125, 0), event.state)
    }

    @Test fun allEightStatesRemainAvailable() {
        val states = listOf(InstallUiState.NotInstalled, InstallUiState.Pending,
            InstallUiState.Downloading(0, 0), InstallUiState.Installing, InstallUiState.Installed,
            InstallUiState.RequiresConfirmation, InstallUiState.Failed(-1, "failure"), InstallUiState.Canceled)
        assertEquals(8, states.map { it::class }.toSet().size)
        assertTrue(states.contains(InstallUiState.Canceled))
    }
}
