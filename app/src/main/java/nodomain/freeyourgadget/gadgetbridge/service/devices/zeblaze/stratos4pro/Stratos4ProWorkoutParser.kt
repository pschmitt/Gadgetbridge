/*  Copyright (C) 2026 Vladimir Tasic

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableBuilder
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryValue
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import java.util.Date
import java.util.TreeMap
import kotlin.math.abs

/** Per-sample series of one workout, filled page by page. Keys are unix seconds. */
internal class WorkoutSeries {
    /**
     * Sample counter across ALL pages: each page repeats the start time, so ts = start +
     * interval * counter (a per-page index piled 1832 samples onto 60 timestamps).
     */
    var sampleIndex = 0
    /** unix seconds -> bpm, from the detail pages; feeds the GPX track points. */
    val hr = TreeMap<Long, Int>()
    /** unix seconds -> steps per minute (detail bit 3). */
    val cadence = TreeMap<Long, Int>()
    /**
     * unix s -> dm/s (bits 1, 14). Pedometer-derived, kept for the record; the exported speed
     * comes from [distance], see deriveSpeed.
     */
    val speed = TreeMap<Long, Int>()
    /** unix seconds -> decimetres, signed (detail bit 5). The GPS file carries no altitude. */
    val elevation = TreeMap<Long, Int>()
    /** unix seconds -> cumulative metres (detail bit 15 is a per-sample delta). */
    val distance = TreeMap<Long, Int>()
    /** Running total of the bit 15 deltas, across pages. */
    var distanceAcc = 0
    /** (km, seconds) from `aa 04`. */
    val splits = ArrayList<Pair<Int, Int>>()
}

/** One `aa 01` list entry. */
internal data class WorkoutListItem(val id: Int, val detailPages: Int)

/** One detail page: its sample interval and start, the sample count and its HR samples. */
internal data class WorkoutDetailPage(val interval: Int, val start: Long, val samples: Int, val hr: Map<Long, Int>)

/** `aa 02` summary: plain sub-TLV from byte 0 (no length header; first entry `01 02 <id:2>`). */
internal class WorkoutSummary(val raw: ByteArray, private val fields: Map<Int, ByteArray>) {
    fun int(flag: Int): Int? = fields[flag]?.beInt()

    val id: Int? get() = int(1)
    val start: Long? get() = int(3)?.toLong()
    val end: Long? get() = int(4)?.toLong()
    val type: Int get() = int(19) ?: 255

    fun toSummaryData(): ActivitySummaryData {
        val d = ActivitySummaryData()
        int(5)?.let { d.add(ActivitySummaryEntries.CALORIES_BURNT, it, ActivitySummaryEntries.UNIT_KCAL) }
        int(6)?.let { d.add(ActivitySummaryEntries.DISTANCE_METERS, it, ActivitySummaryEntries.UNIT_METERS) }
        int(7)?.let { d.add(ActivitySummaryEntries.STEPS, it, ActivitySummaryEntries.UNIT_STEPS) }
        int(8)?.let { d.add(ActivitySummaryEntries.ACTIVE_SECONDS, it, ActivitySummaryEntries.UNIT_SECONDS) }
        int(11)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.HR_MIN, it, ActivitySummaryEntries.UNIT_BPM) }
        int(12)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.HR_MAX, it, ActivitySummaryEntries.UNIT_BPM) }
        // Units checked on real sessions: speed 0.1 m/s, altitude dm, recovery minutes.
        int(9)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.SPEED_AVG, it / 10.0, ActivitySummaryEntries.UNIT_METERS_PER_SECOND) }
        int(10)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.ASCENT_METERS, it, ActivitySummaryEntries.UNIT_METERS) }
        int(26)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.DESCENT_METERS, it, ActivitySummaryEntries.UNIT_METERS) }
        int(27)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.ALTITUDE_MAX, it / 10.0, ActivitySummaryEntries.UNIT_METERS) }
        int(28)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.ALTITUDE_MIN, it / 10.0, ActivitySummaryEntries.UNIT_METERS) }
        int(75)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.PACE_AVG_SECONDS_KM, it, ActivitySummaryEntries.UNIT_SECONDS_PER_KM) }
        // Field 76 is the cadence matching steps/duration; 64 does not (46 vs 79 spm on a
        // hike).
        int(76)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.CADENCE_AVG, it, ActivitySummaryEntries.UNIT_SPM) }
        int(77)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.STRIDE_AVG, it, ActivitySummaryEntries.UNIT_CM) }
        int(13)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.TRAINING_LOAD, it, ActivitySummaryEntries.UNIT_NONE) }
        int(17)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.RECOVERY_TIME, it / 60.0, ActivitySummaryEntries.UNIT_HOURS) }
        int(16)?.takeIf { it > 0 }?.let { d.add(ActivitySummaryEntries.MAXIMUM_OXYGEN_UPTAKE, it, ActivitySummaryEntries.UNIT_ML_KG_MIN) }
        return d
    }
}

/** Parsers for the `e8` workout transfers; no state, no I/O. Times are unix seconds. */
internal object Stratos4ProWorkoutParser {
    /** `aa 01` list: `<count:2>` + 8B items `<id:2><detailPages:2><paceIdx><sectionIdx><spo2><number>`. */
    fun parseList(p: ByteArray): List<WorkoutListItem> {
        val out = ArrayList<WorkoutListItem>()
        var i = 2
        while (i + 8 <= p.size) {
            out.add(WorkoutListItem(u16(p, i), u16(p, i + 2)))
            i += 8
        }
        return out
    }

    /** Null when the id or a sane start/end is missing. */
    fun parseSummary(p: ByteArray): WorkoutSummary? {
        val s = WorkoutSummary(p, parseSubTlv(p))
        val start = s.start ?: return null
        val end = s.end ?: return null
        if (s.id == null || start <= 0 || end <= start) return null
        return s
    }

    /**
     * Detail page: `<id:2><index:2><intervalSec:1><start:4>`, then per sample a 1-2 byte mask (bit
     * 7 = second byte, bits 9-15) and values in bit order: 1 speed(1) 2 kcal(1) 3 cadence(1) 4
     * HR(1) 5 elevation(2, signed) 6 stroke rate(1) 7 swim eff.(1) 9 jump speed(1) 10 fat burn(1)
     * 11 sugar burn(1) 12 ?(1) 13 pace(2) 14 speed(2) 15 distance delta(2). Sample i is at start +
     * i * interval.
     *
     * Units: speed dm/s, elevation dm, cadence steps/min, pace s/km, bit 15 metres.
     */
    fun parseDetailPage(w: WorkoutSeries, p: ByteArray): WorkoutDetailPage? {
        if (p.size < 9) return null
        val interval = u8(p, 4).coerceAtLeast(1)
        val start = be32(p, 5)
        var i = 9
        var n = 0
        val pageHr = TreeMap<Long, Int>()
        while (i < p.size) {
            var mask = u8(p, i)
            var off = 1
            if (mask >= 0x80) {
                if (i + 1 >= p.size) break
                mask = mask or (u8(p, i + 1) shl 8)
                off = 2
            }
            var hr = 0
            var cadence = -1
            var speed = -1
            var elevation: Int? = null
            var distanceDelta = -1
            var ok = true
            for (bit in 1..15) {
                if (bit == 8 || (mask and (1 shl (bit - 1))) == 0) continue
                val width = when (bit) { 5, 13, 14, 15 -> 2; else -> 1 }
                if (i + off + width > p.size) { ok = false; break }
                val b1 = u8(p, i + off)
                val b2 = if (width == 2) u8(p, i + off + 1) else 0
                when (bit) {
                    1 -> speed = b1
                    3 -> cadence = b1
                    4 -> hr = b1
                    // Signed: descending below the start altitude is normal.
                    5 -> elevation = ((b1 shl 8) or b2).toShort().toInt()
                    // 2-byte speed wins over the 1-byte bit 1 when the watch sends both.
                    14 -> speed = (b1 shl 8) or b2
                    15 -> distanceDelta = (b1 shl 8) or b2
                }
                off += width
            }
            if (!ok) break
            val ts = start + w.sampleIndex * interval
            if (hr in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM) {
                w.hr[ts] = hr
                pageHr[ts] = hr
            }
            if (cadence > 0) w.cadence[ts] = cadence
            if (speed >= 0) w.speed[ts] = speed
            elevation?.let { w.elevation[ts] = it }
            if (distanceDelta >= 0) {
                w.distanceAcc += distanceDelta
                w.distance[ts] = w.distanceAcc
            }
            i += off
            n++
            w.sampleIndex++
        }
        return WorkoutDetailPage(interval, start, n, pageHr)
    }

    /**
     * GPS file: `<start:4><end:4><size:4>`, then `<flag:1><len:1><value BE>`; a point ends at flag
     * 13. 7/8 lat/lon * 1e7 (WGS84), 3 altitude m, 1 speed, 11 bearing, 12 accuracy, 13 seconds
     * since the previous point. 9/10 (GCJ-02) are ignored. Null without a single point.
     */
    fun buildTrack(w: WorkoutSeries, start: Long, name: String, gps: ByteArray): ActivityTrack? {
        if (gps.size <= 12) return null
        var t = be32(gps, 0)
        val track = ActivityTrack()
        track.baseTime = Date(start * 1000L)
        track.name = name
        var lat = 0.0; var lon = 0.0
        // This watch's GPS file has no altitude flag, and writing 0 would claim sea level.
        var alt = GPSCoordinate.UNKNOWN_ALTITUDE
        val speeds = deriveSpeed(w.distance)
        var points = 0
        for ((flag, value) in subTlvList(gps, 12)) {
            if (value.isEmpty() || value.size > 4) break
            var v = value.beInt().toLong() and 0xffffffffL
            // Sign-extend 4-byte values.
            if (value.size == 4) v = v.toInt().toLong()
            when (flag) {
                3 -> alt = v.toDouble()
                7 -> lat = v / 1e7
                8 -> lon = v / 1e7
                13 -> {
                    t += v
                    if (lat != 0.0 && lon != 0.0) {
                        val b = ActivityPoint.Builder()
                        b.setTime(t * 1000L)
                        b.setLatitude(lat)
                        b.setLongitude(lon)
                        // No altitude in this watch's GPS file: take it from the detail series
                        // (dm). Set on the coordinate, which feeds GPX <ele> and the FIT altitude.
                        val ele = interpolate(w.elevation, t)?.let { it / 10.0 }
                            ?: alt.takeIf { it > GPSCoordinate.UNKNOWN_ALTITUDE }
                        ele?.let { b.setAltitude(it) }
                        nearest(w.hr, t)?.let { b.setHeartRate(it) }
                        nearest(w.cadence, t)?.let { b.setCadence(it) }
                        nearest(speeds, t)?.let { b.setSpeed(it / 100.0f) }
                        interpolate(w.distance, t)?.let { b.setDistance(it) }
                        track.addTrackPoint(b.build())
                        points++
                    }
                }
            }
        }
        return if (points == 0) null else track
    }

    /**
     * Value of [series] nearest to [t], or null beyond [window] s. Detail samples and GPS points
     * are independent streams, so match by time, not index.
     */
    fun nearest(series: TreeMap<Long, Int>, t: Long, window: Long = 90): Int? {
        val below = series.floorEntry(t)
        val above = series.ceilingEntry(t)
        val best = when {
            below == null -> above
            above == null -> below
            t - below.key <= above.key - t -> below
            else -> above
        } ?: return null
        return if (abs(best.key - t) <= window) best.value else null
    }

    /**
     * Speed in cm/s from the cumulative distance. Detail bit 1 is pedometer-derived: 98% of a 9690
     * m walk but 46% of a 19820 m drive. The distance deltas match the summary total within 4 m on
     * both. Spikes are real (drive p99 18.4 m/s, max 41); do not smooth.
     */
    fun deriveSpeed(distance: TreeMap<Long, Int>): TreeMap<Long, Int> {
        val out = TreeMap<Long, Int>()
        var prev: Map.Entry<Long, Int>? = null
        for (e in distance.entries) {
            prev?.let { p ->
                val dt = e.key - p.key
                val dd = e.value - p.value
                // Skip gaps: across a long pause the mean speed says nothing about either side.
                if (dt in 1..300 && dd >= 0) out[e.key] = ((dd * 100L) / dt).toInt()
            }
            prev = e
        }
        return out
    }

    /**
     * As [nearest], linearly interpolated, for continuous values (altitude, distance): the detail
     * interval is up to a minute, GPS every few seconds.
     */
    fun interpolate(series: TreeMap<Long, Int>, t: Long, window: Long = 90): Double? {
        val below = series.floorEntry(t)
        val above = series.ceilingEntry(t)
        if (below != null && above != null) {
            if (below.key == above.key) return below.value.toDouble()
            val span = (above.key - below.key).toDouble()
            val f = (t - below.key) / span
            return below.value + (above.value - below.value) * f
        }
        // Outside the series: hold the nearest endpoint, but only just outside it.
        val edge = below ?: above ?: return null
        return if (abs(edge.key - t) <= window) edge.value.toDouble() else null
    }

    /** Peaks and km splits the `aa 02` summary lacks. False when there is nothing to add. */
    fun addDetailDerivedStats(d: ActivitySummaryData, w: WorkoutSeries): Boolean {
        if (w.cadence.isEmpty() && w.distance.isEmpty() && w.splits.isEmpty()) return false
        w.cadence.values.maxOrNull()?.takeIf { it > 0 }?.let {
            d.add(ActivitySummaryEntries.CADENCE_MAX, it, ActivitySummaryEntries.UNIT_SPM)
        }
        // From the distance-derived series, not detail bit 1 - see deriveSpeed.
        deriveSpeed(w.distance).values.maxOrNull()?.takeIf { it > 0 }?.let {
            d.add(ActivitySummaryEntries.SPEED_MAX, it / 100.0, ActivitySummaryEntries.UNIT_METERS_PER_SECOND)
        }
        if (w.splits.isNotEmpty()) {
            val t = ActivitySummaryTableBuilder(ActivitySummaryEntries.GROUP_LAPS, "laps_header", listOf("workout_lap", "lap_time"))
            for ((km, sec) in w.splits) {
                t.addRow("lap_$km", listOf(ActivitySummaryValue(km, ActivitySummaryEntries.UNIT_NONE), ActivitySummaryValue(sec, ActivitySummaryEntries.UNIT_SECONDS)))
            }
            t.addToSummaryData(d)
        }
        return true
    }

    /** `aa 04` entry `<id:2><n><km><0><sec:2><0:2><x:2>` -> (km, seconds); km 0 = end of the list. */
    fun parseKmSplit(p: ByteArray): Pair<Int, Int>? =
        if (p.size < 11 || p[3].toInt() == 0) null else u8(p, 3) to u16(p, 5)

    /** Sport ids that get a GPS file request. */
    private val GPS_SPORT_TYPES = setOf(
        1, 2, 8, 9, 35, 36, 37, 43, 68, 124, 179, 243,   // run, ride, climb, walk, hiking, ...
        141, 142, 143, 145, 147, 151, 244, 245,          // open water, orienteering, SUP, rowing, sailing, triathlon, road/mtb
    )

    /** Sport types that get a GPS file request (`aa 09`). */
    fun isGpsSport(type: Int): Boolean = type in GPS_SPORT_TYPES

    /**
     * Workout sport type -> ActivityKind. Field 19 is an index into the watch's sport list (same
     * ids as the `ec` list); 8 is mountaineering.
     */
    fun sportTypeToActivityKind(t: Int): ActivityKind = when (t) {
        0 -> ActivityKind.UNKNOWN
        1 -> ActivityKind.OUTDOOR_RUNNING
        2 -> ActivityKind.OUTDOOR_CYCLING
        3 -> ActivityKind.JUMP_ROPING
        4 -> ActivityKind.POOL_SWIM
        5 -> ActivityKind.BADMINTON
        6 -> ActivityKind.TABLE_TENNIS
        7 -> ActivityKind.TENNIS
        8 -> ActivityKind.MOUNTAINEERING
        9 -> ActivityKind.WALKING
        10 -> ActivityKind.BASKETBALL
        11, 237 -> ActivityKind.SOCCER
        12, 162 -> ActivityKind.BASEBALL
        13 -> ActivityKind.VOLLEYBALL
        14 -> ActivityKind.CRICKET
        15, 70 -> ActivityKind.AMERICAN_FOOTBALL
        16 -> ActivityKind.HOCKEY
        17, 158, 189 -> ActivityKind.DANCE
        18 -> ActivityKind.SPINNING
        19, 117, 168, 169, 170, 171, 172, 173, 174 -> ActivityKind.YOGA
        20 -> ActivityKind.SIT_UPS
        21, 166 -> ActivityKind.TREADMILL
        22 -> ActivityKind.GYMNASTICS
        23 -> ActivityKind.BOATING
        24, 40, 69, 165, 167, 178, 191, 194, 214, 215, 216, 217, 218, 221, 222 -> ActivityKind.EXERCISE
        25, 224 -> ActivityKind.FREE_TRAINING
        26 -> ActivityKind.INDOOR_WALKING
        27 -> ActivityKind.INDOOR_RUNNING
        28, 153, 187 -> ActivityKind.STRENGTH_TRAINING
        29 -> ActivityKind.STEPPER
        30, 161 -> ActivityKind.HORSE_RIDING
        31 -> ActivityKind.ELLIPTICAL_TRAINER
        32 -> ActivityKind.TAI_CHI
        33 -> ActivityKind.SHUTTLECOCK
        34 -> ActivityKind.BOXING
        35 -> ActivityKind.OUTDOOR_WALKING
        36 -> ActivityKind.TRAIL_RUN
        37, 77 -> ActivityKind.SKIING
        38 -> ActivityKind.ICE_HOCKEY
        39 -> ActivityKind.TAEKWONDO
        41 -> ActivityKind.ROWING_MACHINE
        42 -> ActivityKind.AIR_WALKER
        43, 179 -> ActivityKind.HIKING
        44, 140, 156 -> ActivityKind.ATHLETICS
        45, 80 -> ActivityKind.CORE_TRAINING
        46 -> ActivityKind.KARATE
        47 -> ActivityKind.COOLDOWN
        48 -> ActivityKind.CROSS_TRAINING
        49 -> ActivityKind.PILATES
        50 -> ActivityKind.CROSSFIT
        51 -> ActivityKind.FUNCTIONAL_TRAINING
        52 -> ActivityKind.PHYSICAL_TRAINING
        53 -> ActivityKind.ARCHERY
        54, 116 -> ActivityKind.FLEXIBILITY
        55, 186 -> ActivityKind.CARDIO
        56 -> ActivityKind.LATIN_DANCE
        57 -> ActivityKind.STREET_DANCE
        58, 138, 241 -> ActivityKind.KICKBOXING
        59, 182 -> ActivityKind.BALLET
        60 -> ActivityKind.AUSTRALIAN_FOOTBALL
        61 -> ActivityKind.MARTIAL_ARTS
        62 -> ActivityKind.STAIRS
        63 -> ActivityKind.HANDBALL
        64 -> ActivityKind.BOWLING
        65 -> ActivityKind.RACQUETBALL
        66 -> ActivityKind.CURLING
        67 -> ActivityKind.HUNTING
        68, 239 -> ActivityKind.SNOWBOARDING
        71, 183 -> ActivityKind.HANDCYCLING
        72 -> ActivityKind.FISHING
        73 -> ActivityKind.DISC_SPORTS
        74, 197 -> ActivityKind.RUGBY
        75, 133, 195 -> ActivityKind.GOLF
        76 -> ActivityKind.FOLK_DANCE
        78 -> ActivityKind.SNOW_SPORTS
        79, 184 -> ActivityKind.MIND_AND_BODY
        81 -> ActivityKind.SKATING
        82 -> ActivityKind.FITNESS_GAMING
        83 -> ActivityKind.AEROBICS
        84, 177, 180, 188 -> ActivityKind.TRAINING
        85 -> ActivityKind.KENDO
        86 -> ActivityKind.LACROSSE
        87 -> ActivityKind.ROLLING
        88 -> ActivityKind.WRESTLING
        89 -> ActivityKind.FENCING
        90 -> ActivityKind.SOFTBALL
        91 -> ActivityKind.HORIZONTAL_BAR
        92 -> ActivityKind.PARALLEL_BAR
        93 -> ActivityKind.ROLLER_SKATING
        94 -> ActivityKind.HULA_HOOPING
        95 -> ActivityKind.DARTS
        96 -> ActivityKind.PICKLEBALL
        97 -> ActivityKind.HIIT
        98 -> ActivityKind.SHOOTING
        99 -> ActivityKind.JUDO
        100 -> ActivityKind.TRAMPOLINE
        101 -> ActivityKind.SKATEBOARDING
        102, 163 -> ActivityKind.E_SCOOTER
        103 -> ActivityKind.INLINE_SKATING
        104 -> ActivityKind.PARKOUR
        105, 201 -> ActivityKind.DIVING
        106 -> ActivityKind.SURFING
        107 -> ActivityKind.SNORKELING
        108 -> ActivityKind.PULL_UPS
        109 -> ActivityKind.PUSH_UPS
        110 -> ActivityKind.PLANK
        111 -> ActivityKind.ROCK_CLIMBING
        112 -> ActivityKind.HIGH_JUMP
        113 -> ActivityKind.BUNGEE_JUMPING
        114 -> ActivityKind.LONG_JUMP
        115, 129, 130 -> ActivityKind.RUNNING
        118 -> ActivityKind.BEACH_SOCCER
        119 -> ActivityKind.BEACH_VOLLEYBALL
        120 -> ActivityKind.BELLY_DANCE
        121 -> ActivityKind.BIATHLON
        122 -> ActivityKind.BILLIARDS
        123 -> ActivityKind.BMX
        124 -> ActivityKind.CROSS_COUNTRY_SKIING
        125 -> ActivityKind.TUG_OF_WAR
        126 -> ActivityKind.DODGEBALL
        127 -> ActivityKind.DRAGON_BOAT
        128 -> ActivityKind.RAFTING
        131 -> ActivityKind.KITE_FLYING
        132 -> ActivityKind.GATEBALL
        134 -> ActivityKind.INDOOR_CYCLING
        135 -> ActivityKind.JAZZ_DANCE
        136 -> ActivityKind.SEPAK_TAKRAW
        137, 199 -> ActivityKind.CANOEING
        139 -> ActivityKind.POWERBOATING
        141 -> ActivityKind.SWIMMING_OPENWATER
        142 -> ActivityKind.ORIENTEERING
        143 -> ActivityKind.STAND_UP_PADDLEBOARDING
        144 -> ActivityKind.PARACHUTING
        145 -> ActivityKind.ROWING
        146 -> ActivityKind.AUTO_RACING
        147 -> ActivityKind.SAILING
        148, 238 -> ActivityKind.SNOWMOBILING
        149 -> ActivityKind.SQUARE_DANCE
        150 -> ActivityKind.SWING
        151 -> ActivityKind.TRIATHLON
        152, 175, 225 -> ActivityKind.INDOOR_FITNESS
        154 -> ActivityKind.MIXED_MARTIAL_ARTS
        155 -> ActivityKind.WEIGHTLIFTING
        157 -> ActivityKind.BALLROOM_DANCE
        159 -> ActivityKind.EQUESTRIAN
        160 -> ActivityKind.WATER_POLO
        164, 202 -> ActivityKind.PARAGLIDING
        176 -> ActivityKind.CARDIO_COMBAT
        181 -> ActivityKind.STAIR_CLIMBER
        185, 226 -> ActivityKind.STRETCHING
        190 -> ActivityKind.ZUMBA
        192 -> ActivityKind.TEAM_SPORT
        193 -> ActivityKind.VIDEO_GAMING
        196 -> ActivityKind.SQUASH
        198, 205, 206 -> ActivityKind.OTHER_WATER_SPORTS
        200 -> ActivityKind.WATER_SCOOTER
        203 -> ActivityKind.WATER_SKIING
        204 -> ActivityKind.WINDSURFING
        207, 208, 209, 210 -> ActivityKind.SWIMMING
        211 -> ActivityKind.OTHER_WINTER_SPORTS
        212 -> ActivityKind.SLEDDING
        213 -> ActivityKind.SNOWSHOE
        219 -> ActivityKind.DRIVING
        220 -> ActivityKind.MEDITATION
        223 -> ActivityKind.MOBILITY
        227 -> ActivityKind.STEP_AEROBICS
        228 -> ActivityKind.DUMBBELL
        229 -> ActivityKind.BARBELL
        230 -> ActivityKind.DEADLIFT
        231 -> ActivityKind.BURPEE
        232 -> ActivityKind.UPPER_BODY
        233 -> ActivityKind.LOWER_BODY
        234 -> ActivityKind.BACK
        235 -> ActivityKind.ATV
        236 -> ActivityKind.RACKET
        240 -> ActivityKind.KABADDI
        242 -> ActivityKind.MUAY_THAI
        243 -> ActivityKind.TRACK_RUN
        244 -> ActivityKind.ROAD_BIKE
        245 -> ActivityKind.MOUNTAIN_BIKE
        else -> ActivityKind.EXERCISE
    }
}
