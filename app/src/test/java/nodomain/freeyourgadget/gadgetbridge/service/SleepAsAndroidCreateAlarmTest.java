/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;

import nodomain.freeyourgadget.gadgetbridge.entities.Alarm;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class SleepAsAndroidCreateAlarmTest extends TestBase {

    @Test
    public void upcomingTarget_isEnabledOnItsWeekday() {
        final Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 7, 22, 0, 0); // Wednesday
        final Calendar target = (Calendar) now.clone();
        target.set(2026, Calendar.OCTOBER, 8, 7, 0, 0); // Thursday

        final Alarm alarm = SleepAsAndroidSender.createAlarm(2, target.getTimeInMillis(), now);

        Assert.assertTrue(alarm.getEnabled());
        Assert.assertEquals(2, alarm.getPosition());
        Assert.assertEquals(nodomain.freeyourgadget.gadgetbridge.model.Alarm.ALARM_THU, alarm.getRepetition());
        Assert.assertEquals(7, alarm.getHour());
        Assert.assertEquals(0, alarm.getMinute());
    }

    @Test
    public void zeroTimestamp_disablesTheSlot() {
        final Alarm alarm = SleepAsAndroidSender.createAlarm(2, 0L, Calendar.getInstance());

        Assert.assertFalse(alarm.getEnabled());
        Assert.assertEquals(2, alarm.getPosition());
    }
}
