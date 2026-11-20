package vn.demo.liteload.feature.food
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import vn.demo.liteload.navigation.SplitFeatureActivity

class FoodActivity : SplitFeatureActivity() {
    private data class Dish(val image: Int, val title: Int, val body: Int)
    private val dishes = listOf(
        Dish(R.drawable.food_cao_lau, R.string.food_cao_lau_title, R.string.food_cao_lau_body),
        Dish(R.drawable.food_banh_mi, R.string.food_banh_mi_title, R.string.food_banh_mi_body),
        Dish(R.drawable.food_white_rose, R.string.food_white_rose_title, R.string.food_white_rose_body),
        Dish(R.drawable.food_chicken_rice, R.string.food_chicken_rice_title, R.string.food_chicken_rice_body)
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.food_screen)
        findViewById<Button>(R.id.food_back).setOnClickListener { navigateBack() }
        findViewById<ListView>(R.id.food_list).apply {
            adapter = object : BaseAdapter() {
                override fun getCount() = dishes.size
                override fun getItem(position: Int) = dishes[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(R.layout.food_cell, parent, false)
                    view.findViewById<ImageView>(R.id.food_thumbnail).setImageResource(dishes[position].image)
                    view.findViewById<TextView>(R.id.food_caption).setText(dishes[position].title)
                    return view
                }
            }
            setOnItemClickListener { _, _, position, _ -> selectItem(position) }
        }
        renderSelection()
    }
    override fun renderSelection() {
        if (selectedItem >= dishes.size) { selectItem(-1); return }
        findViewById<View>(R.id.food_list).visibility = if (selectedItem < 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.food_detail).visibility = if (selectedItem < 0) View.GONE else View.VISIBLE
        if (selectedItem >= 0) dishes[selectedItem].let {
            findViewById<ImageView>(R.id.food_image).setImageResource(it.image)
            findViewById<TextView>(R.id.food_detail_title).setText(it.title)
            findViewById<TextView>(R.id.food_detail_body).setText(it.body)
        }
    }
}
