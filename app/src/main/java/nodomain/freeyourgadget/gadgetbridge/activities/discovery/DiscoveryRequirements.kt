package nodomain.freeyourgadget.gadgetbridge.activities.discovery

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat

object DiscoveryRequirements {
    enum class Requirement {
        BLUETOOTH_ADAPTER_EXISTS,
        PERMISSIONS_GRANTED,
        LOCATION_ENABLED,
        BLUETOOTH_ENABLED,
    }

    val permissions: List<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun missingPermissions(context: Context): List<String> = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns the first requirement that is not met, or null if discovery can start.
     */
    @JvmStatic
    fun firstUnmet(context: Context): Requirement? {
        val adapter = context.getSystemService<BluetoothManager>()?.adapter
            ?: return Requirement.BLUETOOTH_ADAPTER_EXISTS
        if (missingPermissions(context).isNotEmpty()) {
            return Requirement.PERMISSIONS_GRANTED
        }
        val locationManager = context.getSystemService<LocationManager>()
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return Requirement.LOCATION_ENABLED
        }
        if (!adapter.isEnabled) {
            return Requirement.BLUETOOTH_ENABLED
        }
        return null
    }
}
