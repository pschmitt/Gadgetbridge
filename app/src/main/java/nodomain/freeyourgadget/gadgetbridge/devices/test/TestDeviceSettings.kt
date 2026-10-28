package nodomain.freeyourgadget.gadgetbridge.devices.test

import android.content.Context
import android.content.Intent
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.Language
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.languages
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.test.activity.TestActivitySummaryParser
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.util.Date

fun testDeviceSettings(device: GBDevice, coordinator: TestDeviceCoordinator): DeviceSettingsSpec = deviceSettings {
    action(
        key = TestDeviceConst.PREF_TEST_FEATURES,
        title = R.string.pref_test_features_title,
        summary = R.string.pref_test_features_summary,
        icon = R.drawable.ic_developer_mode,
        connectedOnly = false,
    ) { context, gbDevice ->
        val intent = Intent(context, TestFeaturesActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, gbDevice)
        context.startActivity(intent)
        true
    }

    externalSettings(
        key = LoyaltyCardsSettingsConst.PREF_KEY_LOYALTY_CARDS,
        title = R.string.loyalty_cards,
        icon = R.drawable.ic_loyalty,
        activityClass = LoyaltyCardsSettingsActivity::class.java,
    )

    languages(Language.AUTO, Language.EN_US)

    if (coordinator.worldClocksSlotCount > 0) {
        DeviceSpecificSettingsScreen.DATE_TIME.screen(
            xmlSubScreens = listOf(R.xml.devicesettings_world_clocks),
        ) {}
    }

    if (coordinator.getContactsSlotCount(device) > 0) {
        DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS.screen (
            xmlSubScreens = listOf(R.xml.devicesettings_contacts),
        ) {}
    }

    if (coordinator.supportsGallery(device)) {
        folderPicker(
            key = TestDeviceConst.PREF_TEST_GALLERY_FOLDER,
            title = R.string.photo_gallery,
            icon = R.drawable.ic_photo_library,
            connectedOnly = false,
        )
    }

    DeviceSpecificSettingsScreen.DEVELOPER.screen {
        action(
            key = "pref_developer_add_test_activities",
            title = R.string.pref_developer_add_test_activities_title,
            summary = R.string.pref_developer_add_test_activities_summary,
            icon = R.drawable.ic_auto_awesome,
            connectedOnly = false,
        ) { context, _ ->
            addTestActivities(context, device)
        }
    }
}

private fun addTestActivities(context: Context, device: GBDevice): Boolean {
    try {
        GBApplication.acquireDB().use { dbHandler ->
            val session = dbHandler.daoSession
            val dbDevice = DBHelper.getDevice(device, session)
            val user = DBHelper.getUser(session)
            val parser = TestActivitySummaryParser()
            var startTime = System.currentTimeMillis()

            val summaries = ActivityKind.entries.filter { isRecordedActivity(it) }.map { activityKind ->
                startTime -= TestDeviceRand.randLong(startTime, 4 * 60 * 60 * 1000L, 12 * 60 * 60 * 1000L)
                val duration = TestDeviceRand.randLong(startTime, 15 * 60 * 1000L, 2 * 60 * 60 * 1000L)

                BaseActivitySummary().apply {
                    this.startTime = Date(startTime)
                    endTime = Date(startTime + duration)
                    setDevice(dbDevice)
                    setUser(user)
                    this.activityKind = activityKind.code
                    name = activityKind.name
                    parser.parseBinaryData(this, false)
                }
            }

            session.baseActivitySummaryDao.insertOrReplaceInTx(summaries)
        }
    } catch (e: Exception) {
        GB.toast(context, "Error saving activity summary", Toast.LENGTH_LONG, GB.ERROR, e)
        return false
    }
    return true
}

/**
 * Whether a device records an activity of this kind. Sleep and the states of the wearer are
 * not recorded activities.
 */
private fun isRecordedActivity(activityKind: ActivityKind): Boolean = when (activityKind) {
    ActivityKind.NOT_MEASURED,
    ActivityKind.UNKNOWN,
    ActivityKind.ACTIVITY,
    ActivityKind.NOT_WORN,
    ActivityKind.TRANSITION,
    ActivityKind.VIVOMOVE_HR_TRANSITION -> false

    else -> !ActivityKind.isSleep(activityKind)
}
