package vn.demo.liteload.feature.itinerary
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import vn.demo.liteload.navigation.SplitFeatureActivity

class ItineraryActivity : SplitFeatureActivity() {
    private data class Plan(val title: Int, val duration: Int, val body: Int)
    private val plans = listOf(
        Plan(R.string.itinerary_old_town_title, R.string.itinerary_old_town_duration, R.string.itinerary_old_town_body),
        Plan(R.string.itinerary_sea_fields_title, R.string.itinerary_sea_fields_duration, R.string.itinerary_sea_fields_body),
        Plan(R.string.itinerary_flavours_title, R.string.itinerary_flavours_duration, R.string.itinerary_flavours_body)
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.itinerary_screen)
        findViewById<Button>(R.id.itinerary_back).setOnClickListener { navigateBack() }
        findViewById<ListView>(R.id.itinerary_list).apply {
            adapter = object : ArrayAdapter<Plan>(this@ItineraryActivity, android.R.layout.simple_list_item_2, plans) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                    view.findViewById<TextView>(android.R.id.text1).setText(plans[position].title)
                    view.findViewById<TextView>(android.R.id.text2).setText(plans[position].duration)
                    return view
                }
            }
            setOnItemClickListener { _, _, position, _ -> selectItem(position) }
        }
        renderSelection()
    }
    override fun renderSelection() {
        if (selectedItem >= plans.size) { selectItem(-1); return }
        findViewById<View>(R.id.itinerary_list).visibility = if (selectedItem < 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.itinerary_detail).visibility = if (selectedItem < 0) View.GONE else View.VISIBLE
        if (selectedItem >= 0) plans[selectedItem].let {
            findViewById<TextView>(R.id.itinerary_detail_title).text = getString(it.title) + "\n" + getString(it.duration)
            findViewById<TextView>(R.id.itinerary_detail_body).setText(it.body)
        }
    }
}
