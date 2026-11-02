/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.casio;

import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;

@GsonSerialized
public class CasioLocationPoint {
    public String label = "";
    public long timestamp = 0;
    public GPSCoordinate waypoint = new GPSCoordinate(0.0, 0.0);

    public CasioLocationPoint() {}

    /** Returns the distance and bearing from this point to (lat, lon) */
    public double[] distanceBearingFromLocation(GPSCoordinate other) {
        double[] dist_bear = new double[2];

        dist_bear[0] = GPSCoordinate.distanceHaversine(other, waypoint);
        dist_bear[1] = GPSCoordinate.bearing(other, waypoint);

        return dist_bear;
    }
}
