package nodomain.freeyourgadget.gadgetbridge.devices.test

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory

/**
 * Toggle [TestFeature]s of a test device.
 */
class TestFeaturesActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private val allFeatures = TestFeature.entries.map { it.name }.sorted()
    private val enabled = mutableSetOf<String>()
    private val adapter = FeatureAdapter()
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_features)

        device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE) ?: run {
            finish()
            return
        }

        // An empty set enables all features
        val stored = GBApplication.getDeviceSpecificSharedPrefs(device.address)
            .getStringSet(TestDeviceConst.PREF_TEST_FEATURES, emptySet())
            .orEmpty()
            .filter { it in allFeatures }
        enabled.addAll(stored.ifEmpty { allFeatures })

        findViewById<RecyclerView>(R.id.test_features_list).apply {
            layoutManager = LinearLayoutManager(this@TestFeaturesActivity)
            adapter = this@TestFeaturesActivity.adapter
        }
        showFeatures()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.test_features_menu, menu)
        val searchView = menu.findItem(R.id.test_features_action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(newText: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText.orEmpty()
                showFeatures()
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.test_features_action_select_all -> enabled.addAll(adapter.items)
            R.id.test_features_action_clear -> enabled.removeAll(adapter.items.toSet())
            else -> return super.onOptionsItemSelected(item)
        }
        save()
        showFeatures()
        return true
    }

    /**
     * Displays the features whose name matches [query]. Underscores and spaces are equivalent.
     */
    private fun showFeatures() {
        val words = query.trim().replace('_', ' ')
        adapter.submit(allFeatures.filter { it.replace('_', ' ').contains(words, ignoreCase = true) })
        showCount()
    }

    private fun showCount() {
        supportActionBar?.subtitle = "${enabled.size}/${allFeatures.size}"
    }

    private fun save() {
        val features = if (enabled.size == allFeatures.size) emptySet() else enabled.toSet()
        LOG.debug("Storing test features for {}: {}", device.address, features)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putStringSet(TestDeviceConst.PREF_TEST_FEATURES, features)
        }
        device.sendDeviceUpdateIntent(this)
    }

    private inner class FeatureAdapter : RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {
        var items: List<String> = emptyList()
            private set

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.test_feature_name)
            val checkBox: CheckBox = view.findViewById(R.id.test_feature_checkbox)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_test_feature, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val feature = items[position]
            holder.name.text = feature
            holder.checkBox.isChecked = feature in enabled
            holder.itemView.setOnClickListener {
                if (!enabled.remove(feature)) {
                    enabled.add(feature)
                }
                holder.checkBox.isChecked = feature in enabled
                save()
                showCount()
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TestFeaturesActivity::class.java)
    }
}
