package nodomain.freeyourgadget.gadgetbridge.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.adapter.ContactPickerAdapter
import nodomain.freeyourgadget.gadgetbridge.adapter.ContactPickerAdapter.Entry
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityContactPickerBinding
import nodomain.freeyourgadget.gadgetbridge.entities.Contact
import nodomain.freeyourgadget.gadgetbridge.entities.ContactDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import org.slf4j.LoggerFactory
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class ContactPickerActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityContactPickerBinding
    private lateinit var adapter: ContactPickerAdapter
    private lateinit var gbDevice: GBDevice

    private val selected = LinkedHashSet<Entry>()
    private var allEntries: List<Entry>? = null
    private var existingNames: List<String> = emptyList()
    private var restoredSelectedIds: LongArray? = null
    private var freeSlots = 0
    private var awaitingPermission = false
    private var loadStarted = false
    private var query = ""

    private val countryIso: String by lazy {
        getSystemService<TelephonyManager>()?.networkCountryIso?.takeIf { it.isNotEmpty() }
            ?: Locale.getDefault().country
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        awaitingPermission = false
        updatePermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = intent.getDevice()
        if (device == null) {
            GB.toast("No device provided to ContactPickerActivity", Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }
        gbDevice = device

        adapter = ContactPickerAdapter(selected, ::toggle)
        binding.contactPickerList.layoutManager = LinearLayoutManager(this)
        binding.contactPickerList.adapter = adapter

        binding.contactPickerOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }

        binding.contactPickerConfirm.setOnClickListener { storeSelected() }

        if (savedInstanceState != null) {
            restoredSelectedIds = savedInstanceState.getLongArray(STATE_SELECTED_IDS)
            awaitingPermission = savedInstanceState.getBoolean(STATE_AWAITING_PERMISSION)
        }
        if (!awaitingPermission && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            awaitingPermission = true
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!awaitingPermission) {
            updatePermissionState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AWAITING_PERMISSION, awaitingPermission)
        outState.putLongArray(STATE_SELECTED_IDS, restoredSelectedIds ?: selected.map { it.id }.toLongArray())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_contact_picker, menu)
        val searchView = menu.findItem(R.id.contact_picker_action_search).actionView as SearchView?
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean = false

            override fun onQueryTextChange(text: String?): Boolean {
                query = text?.trim().orEmpty()
                applyFilter()
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updatePermissionState() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            binding.contactPickerList.visibility = View.GONE
            binding.contactPickerProgress.visibility = View.GONE
            binding.contactPickerEmptyText.setText(R.string.permission_not_granted_contacts)
            binding.contactPickerOpenSettings.visibility = View.VISIBLE
            binding.contactPickerEmpty.visibility = View.VISIBLE
            return
        }
        if (loadStarted) {
            return
        }
        loadStarted = true
        binding.contactPickerEmpty.visibility = View.GONE
        binding.contactPickerProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val (existing, entries) = withContext(Dispatchers.IO) {
                val existing = loadExistingContacts()
                existing to queryPhoneNumbers(existing.map { it.number })
            }
            onLoaded(existing, entries)
        }
    }

    private fun loadExistingContacts(): List<Contact> {
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                val daoSession = db.daoSession
                val device = DBHelper.findDevice(gbDevice, daoSession)
                val user = DBHelper.findUser(daoSession)
                if (device == null || user == null) {
                    return emptyList()
                }
                daoSession.contactDao.queryBuilder()
                    .where(
                        ContactDao.Properties.UserId.eq(user.id),
                        ContactDao.Properties.DeviceId.eq(device.id)
                    ).list()
            }
        } catch (e: Exception) {
            LOG.error("Error reading the contacts for the device", e)
            emptyList()
        }
    }

    private fun queryPhoneNumbers(existingNumbers: List<String>): List<Entry> {
        val entries = mutableListOf<Entry>()
        val numbersByContact = mutableMapOf<Long, MutableList<String>>()
        val projection = arrayOf(
            Phone._ID,
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )
        try {
            contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Phone._ID)
                val contactIdColumn = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
                val numberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                val typeColumn = cursor.getColumnIndexOrThrow(Phone.TYPE)
                val labelColumn = cursor.getColumnIndexOrThrow(Phone.LABEL)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberColumn)?.trim()
                    if (number.isNullOrEmpty()) {
                        continue
                    }

                    // A contact can have the same number more than once
                    val contactNumbers = numbersByContact.getOrPut(cursor.getLong(contactIdColumn)) { mutableListOf() }
                    if (contactNumbers.containsNumber(number)) {
                        continue
                    }
                    contactNumbers.add(number)

                    val displayName = cursor.getString(nameColumn)?.trim()
                    val typeLabel = Phone.getTypeLabel(
                        resources,
                        cursor.getInt(typeColumn),
                        cursor.getString(labelColumn)
                    )
                    entries.add(
                        Entry(
                            id = cursor.getLong(idColumn),
                            name = if (displayName.isNullOrEmpty()) number else displayName,
                            number = number,
                            typeLabel = typeLabel.toString(),
                            alreadyAdded = existingNumbers.containsNumber(number),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LOG.error("Error querying the phone contacts", e)
        }
        LOG.debug("Found {} phone numbers in the phone contacts", entries.size)
        return entries
    }

    private fun onLoaded(existing: List<Contact>, entries: List<Entry>) {
        allEntries = entries
        existingNames = existing.map { it.name }
        freeSlots = (gbDevice.deviceCoordinator.getContactsSlotCount(gbDevice) - existing.size).coerceAtLeast(0)

        restoredSelectedIds?.let { restored ->
            val ids = restored.toSet()
            for (entry in entries) {
                if (!entry.alreadyAdded && entry.id in ids && selected.size < freeSlots) {
                    selected.add(entry)
                }
            }
            restoredSelectedIds = null
        }

        binding.contactPickerProgress.visibility = View.GONE
        if (entries.isEmpty()) {
            binding.contactPickerEmptyText.setText(R.string.contact_picker_empty)
            binding.contactPickerOpenSettings.visibility = View.GONE
            binding.contactPickerEmpty.visibility = View.VISIBLE
        } else {
            binding.contactPickerList.visibility = View.VISIBLE
        }
        applyFilter()
        updateSelectionState()
    }

    private fun applyFilter() {
        val entries = allEntries ?: return
        if (query.isEmpty()) {
            adapter.setEntries(entries)
            return
        }
        val nameQuery = foldForSearch(query)
        val numberQuery = query.replace(NOT_DIGITS, "")
        adapter.setEntries(entries.filter { entry ->
            foldForSearch(entry.name).contains(nameQuery) ||
                entry.number.contains(query) ||
                (numberQuery.isNotEmpty() && entry.number.replace(NOT_DIGITS, "").contains(numberQuery))
        })
    }

    private fun toggle(entry: Entry) {
        if (selected.remove(entry)) {
            updateSelectionState()
            return
        }
        if (selected.size >= freeSlots) {
            GB.toast(this, R.string.reminder_no_free_slots_title, Toast.LENGTH_SHORT, GB.INFO)
            return
        }
        if (selected.any { isSameNumber(it.number, entry.number) }) {
            return
        }
        selected.add(entry)
        updateSelectionState()
    }

    private fun updateSelectionState() {
        supportActionBar?.subtitle = getString(R.string.contact_picker_selected_count, selected.size, freeSlots)
        if (selected.isEmpty()) {
            binding.contactPickerConfirm.hide()
        } else {
            binding.contactPickerConfirm.show()
        }
    }

    private fun storeSelected() {
        if (selected.isEmpty()) {
            return
        }
        val entries = selected.toList()
        val names = buildNames(entries, existingNames)
        binding.contactPickerConfirm.isEnabled = false

        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) {
                try {
                    GBApplication.acquireDB().use { db ->
                        val daoSession = db.daoSession
                        val device = DBHelper.getDevice(gbDevice, daoSession)
                        val user = DBHelper.getUser(daoSession)
                        entries.forEachIndexed { i, entry ->
                            val contact = Contact()
                            contact.name = names[i]
                            contact.number = entry.number
                            contact.deviceId = device.id!!
                            contact.userId = user.id!!
                            contact.contactId = UUID.randomUUID().toString()
                            daoSession.insertOrReplace(contact)
                            LOG.debug("Stored contact {} from the phone contacts", contact.contactId)
                        }
                    }
                    true
                } catch (e: Exception) {
                    LOG.error("Error storing the selected contacts", e)
                    false
                }
            }

            if (!stored) {
                GB.toast(this@ContactPickerActivity, R.string.contact_picker_save_failed, Toast.LENGTH_LONG, GB.ERROR)
                binding.contactPickerConfirm.isEnabled = true
                return@launch
            }
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun isSameNumber(a: String, b: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PhoneNumberUtils.areSamePhoneNumber(a, b, countryIso)
        } else {
            @Suppress("DEPRECATION")
            PhoneNumberUtils.compare(a, b)
        }
    }

    private fun List<String>.containsNumber(number: String): Boolean {
        return any { isSameNumber(it, number) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ContactPickerActivity::class.java)

        private const val STATE_SELECTED_IDS = "selected_ids"
        private const val STATE_AWAITING_PERMISSION = "awaiting_permission"
        private val NOT_DIGITS = Regex("[^0-9+]")
        private val MARKS = Regex("\\p{M}+")


        /**
         * Returns the text, in lower case, without accents and other combining marks.
         */
        private fun foldForSearch(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.getDefault())
        }

        /**
         * Returns the name of each new entry, in the same order. A name gets the type label as a suffix
         * only when it matches the name of another new entry or of an existing contact. A counter is
         * added when the name with the suffix also matches.
         */
        private fun buildNames(entries: List<Entry>, existingNames: List<String>): List<String> {
            val nameCounts = (existingNames + entries.map { it.name }).groupingBy { it }.eachCount()
            val used = existingNames.toMutableSet()
            entries.filter { nameCounts[it.name] == 1 }.mapTo(used) { it.name }

            return entries.map { entry ->
                if (nameCounts[entry.name] == 1) {
                    return@map entry.name
                }
                var name = "${entry.name} (${entry.typeLabel})"
                var counter = 2
                while (name in used) {
                    name = "${entry.name} (${entry.typeLabel} ${counter++})"
                }
                used.add(name)
                name
            }
        }
    }
}
