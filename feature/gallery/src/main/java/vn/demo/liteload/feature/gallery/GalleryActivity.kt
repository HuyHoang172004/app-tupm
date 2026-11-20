package vn.demo.liteload.feature.gallery
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import vn.demo.liteload.navigation.SplitFeatureActivity

class GalleryActivity : SplitFeatureActivity() {
    private data class Entry(val image: Int, val title: Int, val body: Int)
    private val entries = listOf(
        Entry(R.drawable.gallery_riverside, R.string.gallery_riverside_title, R.string.gallery_riverside_body),
        Entry(R.drawable.gallery_lanterns, R.string.gallery_lanterns_title, R.string.gallery_lanterns_body),
        Entry(R.drawable.gallery_bridge, R.string.gallery_bridge_title, R.string.gallery_bridge_body),
        Entry(R.drawable.gallery_yellow_wall, R.string.gallery_yellow_wall_title, R.string.gallery_yellow_wall_body),
        Entry(R.drawable.gallery_balcony, R.string.gallery_balcony_title, R.string.gallery_balcony_body),
        Entry(R.drawable.gallery_market, R.string.gallery_market_title, R.string.gallery_market_body),
        Entry(R.drawable.gallery_ricefield, R.string.gallery_ricefield_title, R.string.gallery_ricefield_body),
        Entry(R.drawable.gallery_beach, R.string.gallery_beach_title, R.string.gallery_beach_body),
        Entry(R.drawable.gallery_alley, R.string.gallery_alley_title, R.string.gallery_alley_body),
        Entry(R.drawable.gallery_boat, R.string.gallery_boat_title, R.string.gallery_boat_body),
        Entry(R.drawable.gallery_roof, R.string.gallery_roof_title, R.string.gallery_roof_body),
        Entry(R.drawable.gallery_courtyard, R.string.gallery_courtyard_title, R.string.gallery_courtyard_body)
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery_screen)
        findViewById<Button>(R.id.gallery_back).setOnClickListener { navigateBack() }
        findViewById<GridView>(R.id.gallery_list).apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = entries.size
                override fun getItem(position: Int) = entries[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(R.layout.gallery_cell, parent, false)
                    view.findViewById<ImageView>(R.id.gallery_thumbnail).setImageResource(entries[position].image)
                    view.findViewById<TextView>(R.id.gallery_caption).setText(entries[position].title)
                    return view
                }
            }
            setOnItemClickListener { _, _, position, _ -> selectItem(position) }
        }
        renderSelection()
    }
    override fun renderSelection() {
        if (selectedItem >= entries.size) { selectItem(-1); return }
        findViewById<View>(R.id.gallery_list).visibility = if (selectedItem < 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.gallery_detail).visibility = if (selectedItem < 0) View.GONE else View.VISIBLE
        if (selectedItem >= 0) entries[selectedItem].let {
            findViewById<ImageView>(R.id.gallery_image).setImageResource(it.image)
            findViewById<TextView>(R.id.gallery_detail_title).setText(it.title)
            findViewById<TextView>(R.id.gallery_detail_body).setText(it.body)
        }
    }
}
