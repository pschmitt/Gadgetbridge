package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityNotificationAppListBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationDefaultAction

class NotificationAppListActivity : AbstractGBActivity() {
    private val viewModel: NotificationAppListViewModel by viewModels()
    private lateinit var adapter: NotificationAppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityNotificationAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = NotificationAppListAdapter(
            scope = lifecycleScope,
            onDefaultActionClick = { showDefaultActionDialog(it) },
            onMediaIgnoresListChange = { viewModel.setMediaIgnoresList(it) },
            onAppClick = { appPackageName ->
                startActivity(
                    Intent(this, NotificationAppSettingsActivity::class.java)
                        .putExtra(NotificationAppSettingsActivity.EXTRA_PACKAGE_NAME, appPackageName)
                )
            },
        )
        binding.notificationAppList.layoutManager = LinearLayoutManager(this)
        binding.notificationAppList.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    binding.notificationAppListLoading.isVisible = items == null
                    adapter.submitList(items.orEmpty())
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notification_app_list, menu)
        val searchItem = menu.findItem(R.id.notification_app_list_search)
        val searchView = searchItem.actionView as SearchView
        val query = viewModel.query.value
        if (query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, false)
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        menu.findItem(R.id.notification_app_list_show_system).isChecked = viewModel.showSystemApps.value
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.notification_app_list_show_system -> {
                item.isChecked = !item.isChecked
                viewModel.setShowSystemApps(item.isChecked)
                return true
            }

            R.id.notification_app_list_reset_all -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notification_apps_reset_all)
                    .setMessage(R.string.notification_apps_reset_all_confirm)
                    .setPositiveButton(R.string.reset) { _, _ -> viewModel.resetAll() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDefaultActionDialog(current: NotificationDefaultAction) {
        val actions = NotificationDefaultAction.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_apps_default_action)
            .setSingleChoiceItems(
                actions.map { getString(it.label) }.toTypedArray(),
                actions.indexOf(current),
            ) { dialog, which ->
                viewModel.setDefaultAction(actions[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
