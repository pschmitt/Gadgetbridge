package nodomain.freeyourgadget.gadgetbridge.devices.garmin.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.maps.MapsManager
import nodomain.freeyourgadget.gadgetbridge.webview.CurrentPosition
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.overlay.Marker
import org.slf4j.LoggerFactory
import kotlin.math.abs

/**
 * Map with a waypoint marker and an indicator at the current location.
 * When onPointSelected is set, the user sets the marker with a long press and moves it with a drag.
 */
class WaypointMapController(
    private val context: Context,
    private val mapView: MapView,
    private val onPointSelected: ((LatLong) -> Unit)?,
) : LocationListener {
    val mapsManager = MapsManager(context, mapView)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val accuracyCircle: Circle
    private val headingCone: HeadingLayer
    private val locationDot: FixedPixelCircle
    private val marker: Marker

    var currentLocation: Location? = null
        private set
    val waypoint: LatLong?
        get() = if (marker.isVisible) marker.latLong else null
    private var receivingUpdates = false
    private var positioned = false

    private var declination = 0f
    private var heading: Float? = null
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val headingListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            onRotationChanged(event)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }
    }

    private var dragging = false
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            startDrag(e)
        }
    })

    init {
        mapView.setBuiltInZoomControls(false)
        // Avoid OOM by zooming too far
        mapView.setZoomLevelMin(MIN_ZOOM)
        // loadMaps creates the AndroidGraphicFactory instance, so it must run before the layers are created
        mapsManager.loadMaps()

        val factory = AndroidGraphicFactory.INSTANCE
        val density = context.resources.displayMetrics.density

        val accuracyFill = factory.createPaint().apply {
            color = LOCATION_COLOR and 0x00FFFFFF or 0x30000000
            setStyle(Style.FILL)
        }
        val accuracyStroke = factory.createPaint().apply {
            color = LOCATION_COLOR and 0x00FFFFFF or 0x80000000.toInt()
            strokeWidth = density
            setStyle(Style.STROKE)
        }
        accuracyCircle = Circle(ORIGIN, 0f, accuracyFill, accuracyStroke).apply { isVisible = false }

        val dotFill = factory.createPaint().apply {
            color = LOCATION_COLOR
            setStyle(Style.FILL)
        }
        val dotStroke = factory.createPaint().apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 2 * density
            setStyle(Style.STROKE)
        }
        // The layer multiplies the radius by the display scale factor
        locationDot = FixedPixelCircle(ORIGIN, DOT_RADIUS, dotFill, dotStroke).apply { isVisible = false }

        val headingFill = factory.createPaint().apply {
            color = LOCATION_COLOR and 0x00FFFFFF or 0x50000000
            setStyle(Style.FILL)
        }
        headingCone = HeadingLayer(headingFill).apply { isVisible = false }

        val drawable = AppCompatResources.getDrawable(context, R.drawable.waypoint_marker)!!
        val bitmap = AndroidGraphicFactory.convertToBitmap(drawable)
        // Moves the tip of the pin, to the position
        marker = Marker(ORIGIN, bitmap, 0, -bitmap.height * 5 / 12).apply { isVisible = false }

        mapView.addLayer(accuracyCircle)
        mapView.addLayer(headingCone)
        mapView.addLayer(locationDot)
        mapView.addLayer(marker)

        if (onPointSelected != null) {
            mapView.setOnTouchListener { _, event -> onTouch(event, onPointSelected) }
        }
    }

    /**
     * Moves the view to:
     * - the waypoint
     * - the current location
     * - the location from the preferences
     * - the center of the loaded map files
     * - latitude 0 and longitude 0
     *
     * In that order, after the map view has its size.
     */
    fun showInitialPosition() {
        mapView.post {
            val location = currentLocation?.let { LatLong(it.latitude, it.longitude) } ?: preferencesLocation()
            val bounds = mapsManager.mapBounds
            when {
                marker.isVisible -> mapsManager.setCenter(marker.latLong, DETAIL_ZOOM)
                location != null -> mapsManager.setCenter(location, DETAIL_ZOOM)
                bounds != null -> mapsManager.setBoundingBox(bounds)
                else -> mapsManager.setCenter(ORIGIN, MIN_ZOOM)
            }
            positioned = true
        }
    }

    /**
     * Shows the marker at the waypoint, or hides it when the waypoint is null.
     * Centers the view on the waypoint when it is outside the view.
     */
    fun setWaypoint(waypoint: LatLong?) {
        if (dragging) {
            return
        }
        if (waypoint == null) {
            marker.isVisible = false
            marker.requestRedraw()
            return
        }
        showMarker(waypoint)
        if (positioned && mapView.width > 0 && !mapView.boundingBox.contains(waypoint)) {
            mapView.setCenter(waypoint)
        }
    }

    fun centerOn(location: Location) {
        mapsManager.setCenter(LatLong(location.latitude, location.longitude), DETAIL_ZOOM)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (receivingUpdates || locationManager == null || !hasLocationPermission(context)) {
            return
        }
        val providers = locationManager.allProviders
        for (provider in PROVIDERS) {
            if (!providers.contains(provider)) {
                continue
            }
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
            locationManager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL_MS,
                UPDATE_DISTANCE_M,
                this,
                Looper.getMainLooper()
            )
        }
        rotationSensor?.let {
            sensorManager?.registerListener(headingListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        receivingUpdates = true
    }

    fun stopLocationUpdates() {
        if (!receivingUpdates) {
            return
        }
        locationManager?.removeUpdates(this)
        sensorManager?.unregisterListener(headingListener)
        heading = null
        headingCone.isVisible = false
        receivingUpdates = false
    }

    fun onDestroy() {
        stopLocationUpdates()
        mapsManager.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val previous = currentLocation
        if (previous != null) {
            if (location.time < previous.time) {
                return
            }
            // Do not replace a recent fix with a less accurate one from a different provider
            if (location.time - previous.time < STALE_MS && location.accuracy > previous.accuracy) {
                return
            }
        }
        LOG.debug("Location from {}, accuracy {}m", location.provider, location.accuracy)
        currentLocation = location

        val latLong = LatLong(location.latitude, location.longitude)
        accuracyCircle.setLatLong(latLong)
        accuracyCircle.radius = location.accuracy
        accuracyCircle.isVisible = location.hasAccuracy()
        locationDot.setLatLong(latLong)
        locationDot.isVisible = true
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            location.time
        ).declination
        heading?.let { headingCone.update(latLong, it) }
        accuracyCircle.requestRedraw()
    }

    private fun onRotationChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val (axisX, axisY) = when (ContextCompat.getDisplayOrDefault(context).rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)
        // The sensor gives the heading from magnetic north
        val target = Math.toDegrees(orientation[0].toDouble()).toFloat() + declination

        val previous = heading
        val next = if (previous == null) {
            target
        } else {
            // Smooth the heading along the shortest direction of rotation
            val delta = ((target - previous) % 360 + 540) % 360 - 180
            if (abs(delta) < MIN_HEADING_CHANGE) {
                return
            }
            (previous + delta * HEADING_SMOOTHING + 360) % 360
        }
        heading = next

        val location = currentLocation ?: return
        headingCone.update(LatLong(location.latitude, location.longitude), next)
        headingCone.isVisible = true
        headingCone.requestRedraw()
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }

    private fun onTouch(event: MotionEvent, onPointSelected: (LatLong) -> Unit): Boolean {
        gestureDetector.onTouchEvent(event)
        if (!dragging) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> moveMarker(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moveMarker(event)
                dragging = false
                onPointSelected(marker.latLong)
            }
        }
        return true
    }

    private fun startDrag(event: MotionEvent) {
        dragOffsetX = 0.0
        dragOffsetY = 0.0
        if (marker.isVisible) {
            val markerPoint = mapView.mapViewProjection.toPixels(marker.latLong)
            val bitmap = marker.bitmap
            if (markerPoint != null
                && abs(event.x - markerPoint.x) <= bitmap.width / 2.0
                && event.y <= markerPoint.y
                && event.y >= markerPoint.y - bitmap.height
            ) {
                dragOffsetX = markerPoint.x - event.x
                dragOffsetY = markerPoint.y - event.y
            }
        }
        dragging = true
        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        // The map view received the down event, so it must stop the scroll and zoom gestures
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        mapView.onTouchEvent(cancel)
        cancel.recycle()

        moveMarker(event)
    }

    private fun moveMarker(event: MotionEvent) {
        val latLong = try {
            mapView.mapViewProjection.fromPixels(event.x + dragOffsetX, event.y + dragOffsetY)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return
        showMarker(latLong)
    }

    private fun showMarker(latLong: LatLong) {
        marker.latLong = latLong
        marker.isVisible = true
        marker.requestRedraw()
    }

    private fun preferencesLocation(): LatLong? {
        val location = CurrentPosition().lastKnownLocation
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            return null
        }
        return LatLong(location.latitude, location.longitude)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WaypointMapController::class.java)

        private val ORIGIN = LatLong(0.0, 0.0)
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        private const val LOCATION_COLOR = 0xFF1E88E5.toInt()
        private const val DETAIL_ZOOM: Byte = 15
        private const val MIN_ZOOM: Byte = 8
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val UPDATE_DISTANCE_M = 5f
        private const val STALE_MS = 10_000L
        private const val DOT_RADIUS = 6f
        private const val MIN_HEADING_CHANGE = 1f
        private const val HEADING_SMOOTHING = 0.3f

        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        fun hasLocationPermission(context: Context): Boolean {
            return LOCATION_PERMISSIONS.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
