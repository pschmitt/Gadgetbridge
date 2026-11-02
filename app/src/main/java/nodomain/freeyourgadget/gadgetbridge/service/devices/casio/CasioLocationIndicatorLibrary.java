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

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized;

@GsonSerialized
public class CasioLocationIndicatorLibrary {
    private static final Logger LOG = LoggerFactory.getLogger(CasioLocationIndicatorLibrary.class);

    public static final String PREF_LOCATION_INDICATOR_LIBRARY = "casio_location_indicators";
    public static final String CONFIG_LOCATION_INDICATOR_ACTIVE = "casio_location_indicator_active";
    private static final Gson GSON = new Gson();

    public List<CasioLocationPoint> locations = new ArrayList<>();
    public int activeIndex = -1;

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CasioLocationIndicatorLibrary fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new CasioLocationIndicatorLibrary();
        }
        try {
            CasioLocationIndicatorLibrary lib = GSON.fromJson(json, CasioLocationIndicatorLibrary.class);
            if (lib == null || lib.locations == null) {
                LOG.warn("Stored locations library is incomplete, discarding it");
                return new CasioLocationIndicatorLibrary();
            }
            if (lib.activeIndex < -1 || lib.activeIndex >= lib.locations.size()) lib.activeIndex = -1;
            return lib;
        } catch (Exception e) {
            LOG.error("Failed to parse stored locations indicator library, discarding it", e);
            return new CasioLocationIndicatorLibrary();
        }
    }

    public boolean add(CasioLocationPoint p) {
        locations.add(p);
        // Always use the new location added by the watch as the active one; 
        // this is expected by watch manual
        activeIndex = locations.size() - 1;
        return true;
    }

    public void remove(int index) {
        if (index < 0 || index >= locations.size()) return;
        locations.remove(index);
        if (activeIndex == index) {
            activeIndex = locations.isEmpty() ? -1 : locations.size() - 1;
        } else if (activeIndex > index) {
            activeIndex--;
        }
    }

    public CasioLocationPoint getActive() {
        if (activeIndex < 0 || activeIndex >= locations.size()) return null;
        return locations.get(activeIndex);
    }

    public void setActive(int index) {
        if (index >= -1 && index < locations.size()) activeIndex = index;
    }
}
