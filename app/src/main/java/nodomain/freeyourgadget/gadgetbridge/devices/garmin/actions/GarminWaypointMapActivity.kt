package nodomain.freeyourgadget.gadgetbridge.devices.garmin.actions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.maps.MapsSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.maps.MapsSettingsFragment
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWaypointMapBinding
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.mapsforge.core.model.LatLong
import org.slf4j.LoggerFactory

/**
 * Full-screen map to select the position of a waypoint.
 */
class GarminWaypointMapActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityWaypointMapBinding
    private lateinit var mapController: WaypointMapController
    private var mapValid = true
    private var initialPoint: LatLong? = null
    private var selectedPoint: LatLong? = null

    private var pendingLocationAction: ((Location) -> Unit)? = null
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val action = pendingLocationAction
            pendingLocationAction = null
            if (result.values.any { it }) {
                mapController.startLocationUpdates()
                if (action != null) {
                    withCurrentLocation(action)
                }
            } else {
                GB.toast(this, R.string.activity_send_waypoint_location_unavailable, Toast.LENGTH_SHORT, GB.WARN)
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mapValid = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWaypointMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialPoint = intent.getLatLong()
        selectedPoint = savedInstanceState?.let { readLatLong(it) } ?: initialPoint

        mapController = WaypointMapController(this, binding.mapView) { latLong ->
            LOG.debug("Waypoint selected on the map: {}", latLong)
            selectPoint(latLong)
        }
        mapController.setWaypoint(selectedPoint)
        mapController.showInitialPosition()
        binding.done.isEnabled = selectedPoint != null
        updateMissingMapsWarning()

        binding.mapsSettings.setOnClickListener {
            startActivity(Intent(this, MapsSettingsActivity::class.java))
        }
        binding.centerOnLocation.setOnClickListener {
            withCurrentLocation { mapController.centerOn(it) }
        }
        binding.useLocation.setOnClickListener {
            withCurrentLocation {
                val latLong = LatLong(it.latitude, it.longitude)
                mapController.centerOn(it)
                mapController.setWaypoint(latLong)
                selectPoint(latLong)
            }
        }
        binding.done.setOnClickListener {
            val point = selectedPoint
            if (point != null && point != initialPoint) {
                setResult(RESULT_OK, Intent().putLatLong(point))
            }
            finish()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(MapsSettingsFragment.ACTION_SETTING_CHANGE)
        )
    }

    override fun onResume() {
        super.onResume()
        if (!mapValid) {
            mapController.mapsManager.reload()
            updateMissingMapsWarning()
            mapValid = true
        }
        mapController.startLocationUpdates()
    }

    override fun onPause() {
        mapController.stopLocationUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        mapController.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedPoint?.let {
            outState.putDouble(EXTRA_LATITUDE, it.latitude)
            outState.putDouble(EXTRA_LONGITUDE, it.longitude)
        }
    }

    private fun selectPoint(latLong: LatLong) {
        selectedPoint = latLong
        binding.done.isEnabled = true
    }

    private fun updateMissingMapsWarning() {
        binding.missingMapsWarning.visibility =
            if (mapController.mapsManager.isMapLoaded) View.GONE else View.VISIBLE
    }

    /**
     * Runs [action] with the current location. Asks for the location permission first when it is missing.
     */
    private fun withCurrentLocation(action: (Location) -> Unit) {
        if (!WaypointMapController.hasLocationPermission(this)) {
            pendingLocationAction = action
            locationPermissionLauncher.launch(WaypointMapController.LOCATION_PERMISSIONS)
            return
        }
        val location = mapController.currentLocation
        if (location == null) {
            GB.toast(this, R.string.activity_send_waypoint_location_unavailable, Toast.LENGTH_SHORT, GB.WARN)
            return
        }
        action(location)
    }

    /**
     * Opens the map at an optional waypoint, and returns the selected point when the user changed it.
     */
    class SelectPoint : ActivityResultContract<LatLong?, LatLong?>() {
        override fun createIntent(context: Context, input: LatLong?): Intent {
            val intent = Intent(context, GarminWaypointMapActivity::class.java)
            input?.let { intent.putLatLong(it) }
            return intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): LatLong? {
            if (resultCode != RESULT_OK) {
                return null
            }
            return intent?.getLatLong()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GarminWaypointMapActivity::class.java)

        private const val EXTRA_LATITUDE = "latitude"
        private const val EXTRA_LONGITUDE = "longitude"

        private fun Intent.putLatLong(latLong: LatLong): Intent {
            putExtra(EXTRA_LATITUDE, latLong.latitude)
            putExtra(EXTRA_LONGITUDE, latLong.longitude)
            return this
        }

        private fun Intent.getLatLong(): LatLong? = extras?.let { readLatLong(it) }

        private fun readLatLong(bundle: Bundle): LatLong? {
            if (!bundle.containsKey(EXTRA_LATITUDE) || !bundle.containsKey(EXTRA_LONGITUDE)) {
                return null
            }
            return LatLong(bundle.getDouble(EXTRA_LATITUDE), bundle.getDouble(EXTRA_LONGITUDE))
        }
    }
}
