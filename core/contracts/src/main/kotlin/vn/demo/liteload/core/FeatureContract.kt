package vn.demo.liteload.core

object FeatureContract {
    const val GALLERY_MODULE = "gallery"
    const val GALLERY_ACTIVITY = "vn.demo.liteload.feature.gallery.GalleryActivity"

    const val AUDIO_GUIDE_MODULE = "audio_guide"
    const val AUDIO_GUIDE_ACTIVITY = "vn.demo.liteload.feature.audioguide.AudioGuideActivity"

    const val ITINERARY_MODULE = "itinerary"
    const val ITINERARY_ACTIVITY = "vn.demo.liteload.feature.itinerary.ItineraryActivity"

    const val FOOD_MODULE = "food"
    const val FOOD_ACTIVITY = "vn.demo.liteload.feature.food.FoodActivity"

    val MODULES = setOf(
        GALLERY_MODULE,
        AUDIO_GUIDE_MODULE,
        ITINERARY_MODULE,
        FOOD_MODULE
    )
}
