package nodomain.freeyourgadget.gadgetbridge.devices.garmin.actions

import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a cone that points from a location, in the direction of a heading, in degrees from north.
 */
class HeadingLayer(private val paint: Paint) : Layer() {
    private val path = AndroidGraphicFactory.INSTANCE.createPath()
    private var latLong: LatLong? = null
    private var heading = 0f

    @Synchronized
    fun update(latLong: LatLong, heading: Float) {
        this.latLong = latLong
        this.heading = heading
    }

    @Synchronized
    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation,
    ) {
        val latLong = latLong ?: return
        val mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.tileSize)
        val x = (MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize) - topLeftPoint.x).toFloat()
        val y = (MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize) - topLeftPoint.y).toFloat()
        val length = LENGTH * displayModel.scaleFactor

        val center = Math.toRadians(heading.toDouble())
        val left = center - HALF_ANGLE
        val right = center + HALF_ANGLE
        val controlLength = length / cos(HALF_ANGLE)

        path.clear()
        path.moveTo(x, y)
        path.lineTo(x + (length * sin(left)).toFloat(), y - (length * cos(left)).toFloat())
        path.quadTo(
            x + (controlLength * sin(center)).toFloat(),
            y - (controlLength * cos(center)).toFloat(),
            x + (length * sin(right)).toFloat(),
            y - (length * cos(right)).toFloat()
        )
        path.close()
        canvas.drawPath(path, paint)
    }

    companion object {
        private const val LENGTH = 40f
        private val HALF_ANGLE = Math.toRadians(30.0)
    }
}
