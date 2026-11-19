package vn.demo.liteload

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val optimizationLabel = if (BuildConfig.SIZE_OPTIMIZATION) {
            R.string.optimization_enabled
        } else {
            R.string.optimization_disabled
        }

        findViewById<TextView>(R.id.optimization_status).setText(optimizationLabel)
        findViewById<TextView>(R.id.version_value).text = BuildConfig.VERSION_NAME
    }
}
