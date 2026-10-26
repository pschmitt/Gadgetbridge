/*  Copyright (C) 2026 kernautomacao

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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BettdowFB051CoordinatorTest {
    @Test
    public void exposesObservedMoyoungV2Parameters() {
        final BettdowFB051Coordinator coordinator = new BettdowFB051Coordinator();

        assertEquals(508, coordinator.getMtu());
        assertEquals(8, coordinator.getAlarmSlotCount(null));
    }
}
