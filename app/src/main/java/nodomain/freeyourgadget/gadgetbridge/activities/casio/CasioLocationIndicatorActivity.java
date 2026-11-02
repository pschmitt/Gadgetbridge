/*  Copyright (C) 2026 Davide Gessa

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
package nodomain.freeyourgadget.gadgetbridge.activities.casio;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.CasioLocationPoint;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.CasioLocationIndicatorLibrary;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.WaypointHelper;
import nodomain.freeyourgadget.gadgetbridge.util.gpx.GpxParser;
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxFile;
import nodomain.freeyourgadget.gadgetbridge.util.gpx.model.GpxWaypoint;

public class CasioLocationIndicatorActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(CasioLocationIndicatorActivity.class);
    private static final String NS_GPX_URI = "http://www.topografix.com/GPX/1/1";

    private GBDevice gbDevice;
    protected CasioLocationIndicatorLibrary library;
    protected LocationAdapter adapter;
    private TextView emptyView;
    private FloatingActionButton addButton;

    private final ActivityResultLauncher<String[]> importGpxLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importGpx(uri);
            });

    private final ActivityResultLauncher<String> exportGpxLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/gpx+xml"),
            uri -> {
                if (uri != null) exportGpx(uri);
            });

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_casio_location_indicator);

        gbDevice = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (gbDevice == null) {
            finish();
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        library = loadLibrary();

        emptyView = findViewById(R.id.casio_location_empty);
        addButton = findViewById(R.id.casio_location_add);
        final RecyclerView list = findViewById(R.id.casio_location_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LocationAdapter();
        list.setAdapter(adapter);

        addButton.setOnClickListener(v -> onAddClicked());
        refreshEmpty();
    }

    private CasioLocationIndicatorLibrary loadLibrary() {
        final String json = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress())
                .getString(CasioLocationIndicatorLibrary.PREF_LOCATION_INDICATOR_LIBRARY, null);
        return CasioLocationIndicatorLibrary.fromJson(json);
    }

    protected void persistAndRefresh() {
        GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).edit()
                .putString(CasioLocationIndicatorLibrary.PREF_LOCATION_INDICATOR_LIBRARY, library.toJson())
                .apply();
        adapter.notifyDataSetChanged();
        refreshEmpty();
    }

    private void refreshEmpty() {
        final boolean empty = library.locations.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }


    protected void openEditor(final int index) {
        final boolean isNew = index < 0;
        final CasioLocationPoint working = isNew ? defaultPoint() : clonePoint(library.locations.get(index));

        final View root = LayoutInflater.from(this)
                .inflate(R.layout.dialog_casio_location_indicator_edit, null);
        final EditText labelView = root.findViewById(R.id.location_label);
        final EditText latView = root.findViewById(R.id.location_lat);
        final EditText lonView = root.findViewById(R.id.location_lon);
        final EditText elevView = root.findViewById(R.id.location_elevation);
        final View useCurrentButton = root.findViewById(R.id.location_use_current);

        labelView.setText(working.label);
        if (!isNew) {
            latView.setText(String.format(Locale.ROOT, "%.6f", working.waypoint.getLatitude()));
            lonView.setText(String.format(Locale.ROOT, "%.6f", working.waypoint.getLongitude()));
            if (working.waypoint.hasAltitude()) {
                elevView.setText(String.format(Locale.ROOT, "%.0f", working.waypoint.getAltitude()));
            }
        }

        useCurrentButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_SHORT).show();
                return;
            }
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location fix = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (fix == null) fix = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (fix == null) {
                Toast.makeText(this, R.string.casio_location_indicator_no_fix, Toast.LENGTH_SHORT).show();
                return;
            }
            latView.setText(String.format(Locale.ROOT, "%.6f", fix.getLatitude()));
            lonView.setText(String.format(Locale.ROOT, "%.6f", fix.getLongitude()));
            if (fix.hasAltitude()) {
                elevView.setText(String.format(Locale.ROOT, "%.0f", fix.getAltitude()));
            }
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.pref_casio_location_indicator_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final Double lat = parseCoordinate(latView, -90.0, 90.0);
                    final Double lon = parseCoordinate(lonView, -180.0, 180.0);
                    if (lat == null || lon == null) {
                        Toast.makeText(this, R.string.casio_location_indicator_invalid_coordinates,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final Double elev = parseElevation(elevView);

                    working.label = labelView.getText().toString().trim();
                    if (working.label.isEmpty()) {
                        working.label = getString(R.string.casio_location_indicator_default_label,
                                (isNew ? library.locations.size() : index) + 1);
                    }
                    working.waypoint = new GPSCoordinate(lon, lat,
                            elev != null ? elev : GPSCoordinate.UNKNOWN_ALTITUDE);
                    if (isNew) {
                        working.timestamp = System.currentTimeMillis();
                    }

                    final boolean editingActive = !isNew && index == library.activeIndex;
                    if (isNew) {
                        library.add(working);
                    } else {
                        library.locations.set(index, working);
                    }
                    persistAndRefresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Nullable
    private static Double parseCoordinate(final EditText view, final double min, final double max) {
        try {
            final double v = Double.parseDouble(view.getText().toString().trim());
            return (v >= min && v <= max) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Double parseElevation(final EditText view) {
        final String s = view.getText().toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private static CasioLocationPoint defaultPoint() {
        return new CasioLocationPoint();
    }

    private static CasioLocationPoint clonePoint(final CasioLocationPoint src) {
        final CasioLocationPoint copy = new CasioLocationPoint();
        copy.label = src.label;
        copy.timestamp = src.timestamp;
        copy.waypoint = src.waypoint;
        return copy;
    }

    private void onAddClicked() {
        openEditor(-1);
    }

    private void sharePoint(final int index) {
        if (index < 0 || index >= library.locations.size()) return;
        final CasioLocationPoint p = library.locations.get(index);
        final Double elevation = p.waypoint.hasAltitude() ? p.waypoint.getAltitude() : null;
        final String geoUri = new WaypointHelper(p.label, p.waypoint.getLatitude(),
                p.waypoint.getLongitude(), elevation).toString();
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, p.label);
        intent.putExtra(Intent.EXTRA_TEXT, geoUri);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    static String summarize(final Context context, final CasioLocationPoint p) {
        final String coords = String.format(Locale.ROOT, "%.5f, %.5f",
                p.waypoint.getLatitude(), p.waypoint.getLongitude());
        final String added = DateTimeUtils.formatDateTimeRelative(context, new Date(p.timestamp));
        return coords + " · " + added;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_casio_location_indicator, menu);
        return true;
    }

    private void importGpx(final Uri uri) {
        final List<CasioLocationPoint> imported = new ArrayList<>();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            final GpxFile gpx = new GpxParser(in).getGpxFile();
            for (final GpxWaypoint wpt : gpx.getWaypoints()) {
                final CasioLocationPoint p = new CasioLocationPoint();
                final String name = wpt.getName();
                p.label = name != null && !name.isEmpty() ? name
                        : getString(R.string.casio_location_indicator_default_label,
                                library.locations.size() + imported.size() + 1);
                p.timestamp = wpt.getTime() != null ? wpt.getTime().getTime() : System.currentTimeMillis();
                p.waypoint = new GPSCoordinate(wpt.getLongitude(), wpt.getLatitude(),
                        wpt.hasAltitude() ? wpt.getAltitude() : GPSCoordinate.UNKNOWN_ALTITUDE);
                imported.add(p);
            }
        } catch (final Exception e) {
            LOG.error("Failed to import GPX", e);
            Toast.makeText(this, R.string.casio_location_indicator_import_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (imported.isEmpty()) {
            Toast.makeText(this, R.string.casio_location_indicator_import_no_waypoints, Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean hadActive = library.activeIndex >= 0;
        library.locations.addAll(imported);
        if (!hadActive) {
            library.activeIndex = 0;
        }
        persistAndRefresh();
        Toast.makeText(this, getString(R.string.casio_location_indicator_imported, imported.size()),
                Toast.LENGTH_SHORT).show();
    }

    private void exportGpx(final Uri uri) {
        if (library.locations.isEmpty()) {
            Toast.makeText(this, R.string.casio_location_indicator_empty_export, Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            writeGpx(out);
            Toast.makeText(this, R.string.casio_location_indicator_exported, Toast.LENGTH_SHORT).show();
        } catch (final Exception e) {
            LOG.error("Failed to export GPX", e);
            Toast.makeText(this, R.string.casio_location_indicator_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeGpx(final OutputStream out) throws IOException {
        final XmlSerializer ser = Xml.newSerializer();
        ser.setOutput(out, StandardCharsets.UTF_8.name());
        ser.startDocument(StandardCharsets.UTF_8.name(), true);
        ser.setPrefix("", NS_GPX_URI);
        ser.startTag(NS_GPX_URI, "gpx");
        ser.attribute(null, "version", "1.1");
        ser.attribute(null, "creator", GBApplication.app().getNameAndVersion());
        for (final CasioLocationPoint p : library.locations) {
            ser.startTag(NS_GPX_URI, "wpt");
            ser.attribute(null, "lat", String.format(Locale.ROOT, "%.6f", p.waypoint.getLatitude()));
            ser.attribute(null, "lon", String.format(Locale.ROOT, "%.6f", p.waypoint.getLongitude()));
            if (p.waypoint.hasAltitude()) {
                ser.startTag(NS_GPX_URI, "ele")
                        .text(String.format(Locale.ROOT, "%.1f", p.waypoint.getAltitude()))
                        .endTag(NS_GPX_URI, "ele");
            }
            if (!p.label.isEmpty()) {
                ser.startTag(NS_GPX_URI, "name").text(p.label).endTag(NS_GPX_URI, "name");
            }
            if (p.timestamp > 0) {
                ser.startTag(NS_GPX_URI, "time")
                        .text(DateTimeUtils.formatIso8601UTC(new Date(p.timestamp)))
                        .endTag(NS_GPX_URI, "time");
            }
            ser.endTag(NS_GPX_URI, "wpt");
        }
        ser.endTag(NS_GPX_URI, "gpx");
        ser.endDocument();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.casio_location_import_gpx) {
            importGpxLauncher.launch(new String[]{"application/gpx+xml", "text/xml", "application/xml", "*/*"});
            return true;
        }
        if (item.getItemId() == R.id.casio_location_export_gpx) {
            exportGpxLauncher.launch("casio_location_points.gpx");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final android.widget.RadioButton active;
            final TextView label;
            final TextView summary;
            final android.widget.ImageButton share;
            final android.widget.ImageButton delete;
            VH(final View v) {
                super(v);
                active = v.findViewById(R.id.casio_location_item_active);
                label = v.findViewById(R.id.casio_location_item_label);
                summary = v.findViewById(R.id.casio_location_item_summary);
                share = v.findViewById(R.id.casio_location_item_share);
                delete = v.findViewById(R.id.casio_location_item_delete);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_casio_location_indicator, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final VH h, final int position) {
            final CasioLocationPoint p = library.locations.get(position);
            h.label.setText(p.label);
            h.summary.setText(summarize(h.itemView.getContext(), p));
            h.active.setChecked(position == library.activeIndex);
            h.active.setOnClickListener(v -> {
                library.setActive(h.getBindingAdapterPosition());
                persistAndRefresh();
            });
            h.itemView.setOnClickListener(v -> openEditor(h.getBindingAdapterPosition()));
            h.share.setOnClickListener(v -> sharePoint(h.getBindingAdapterPosition()));
            h.delete.setOnClickListener(v -> {
                library.remove(h.getBindingAdapterPosition());
                persistAndRefresh();
            });
        }

        @Override
        public int getItemCount() {
            return library.locations.size();
        }
    }
}
