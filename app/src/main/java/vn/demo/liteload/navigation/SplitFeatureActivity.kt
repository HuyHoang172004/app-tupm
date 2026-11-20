package vn.demo.liteload.navigation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.google.android.play.core.splitcompat.SplitCompat

/** Small shared shell; all content, XML and media remain in the individual splits. */
abstract class SplitFeatureActivity : ComponentActivity() {
    protected var selectedItem: Int = -1
        private set
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        SplitCompat.installActivity(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedItem = savedInstanceState?.getInt("selected_item", -1) ?: -1
        onBackPressedDispatcher.addCallback(this) { navigateBack() }
    }
    protected fun selectItem(index: Int) { selectedItem = index; renderSelection() }
    protected fun navigateBack() {
        if (selectedItem >= 0) selectItem(-1) else finish()
    }
    protected abstract fun renderSelection()
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_item", selectedItem)
        super.onSaveInstanceState(outState)
    }
}
