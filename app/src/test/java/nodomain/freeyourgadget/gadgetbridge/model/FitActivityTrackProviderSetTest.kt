package nodomain.freeyourgadget.gadgetbridge.model

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.ExerciseCategory
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitActivityTrackProviderSetTest {

    @Test
    fun setCategorySurvivesPaddedValueArray() {
        val fitFile = FitFile.parseIncoming(
            javaClass.getResourceAsStream("/TestFitMessageTypeParsing.fit")!!.readBytes()
        )
        val summary = BaseActivitySummary()
        summary.activityKind = ActivityKind.STRENGTH_TRAINING.code

        val track = requireNotNull(FitActivityTrackProvider().getActivityTrack(summary, fitFile))

        assertEquals(1, fitFile.records.count { it is FitSet })
        assertEquals(1, track.sets.size)
        assertEquals(ExerciseCategory.CATEGORY_CALF_RAISE, track.sets[0].exerciseCategory)
        assertEquals(Integer.valueOf(1), track.sets[0].categorySubtype)
    }

    @Test
    fun picksFirstRealCategoryWithItsOwnSubtype() {
        val picked = FitActivityTrackProvider.pickSetCategory(
            arrayOf<Any?>(null, ExerciseCategory.CATEGORY_SQUAT),
            arrayOf<Any?>(0, 3)
        )

        assertEquals(ExerciseCategory.CATEGORY_SQUAT, picked.category)
        assertEquals(Integer.valueOf(3), picked.subtype)
    }

    @Test
    fun skipsUnknownCategorySlots() {
        val picked = FitActivityTrackProvider.pickSetCategory(
            arrayOf<Any?>(ExerciseCategory.CATEGORY_UNKNOWN, ExerciseCategory.CATEGORY_BENCH_PRESS),
            arrayOf<Any?>(9, 4)
        )

        assertEquals(ExerciseCategory.CATEGORY_BENCH_PRESS, picked.category)
        assertEquals(Integer.valueOf(4), picked.subtype)
    }

    @Test
    fun noRealCategoryYieldsNothing() {
        val picked = FitActivityTrackProvider.pickSetCategory(
            arrayOf<Any?>(null, ExerciseCategory.CATEGORY_UNKNOWN),
            arrayOf<Any?>(1, 2)
        )

        assertNull(picked.category)
        assertNull(picked.subtype)
    }
}
