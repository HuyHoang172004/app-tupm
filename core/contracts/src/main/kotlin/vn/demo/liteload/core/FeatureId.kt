package vn.demo.liteload.core

/** Stable split names and entry points shared with Home. Never import feature classes in base. */
enum class FeatureId(val moduleName: String, val activityClassName: String) {
    GALLERY(FeatureContract.GALLERY_MODULE, FeatureContract.GALLERY_ACTIVITY),
    AUDIO_GUIDE(FeatureContract.AUDIO_GUIDE_MODULE, FeatureContract.AUDIO_GUIDE_ACTIVITY),
    ITINERARY(FeatureContract.ITINERARY_MODULE, FeatureContract.ITINERARY_ACTIVITY),
    FOOD(FeatureContract.FOOD_MODULE, FeatureContract.FOOD_ACTIVITY)
}
