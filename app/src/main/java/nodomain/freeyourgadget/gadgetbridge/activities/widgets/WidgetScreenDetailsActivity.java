/*  Copyright (C) 2023-2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.activities.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetLayout;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetManager;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetPart;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetPartSubtype;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetScreen;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetType;
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWidgetScreenDetailsBinding;
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemWidgetScreenSlotBinding;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class WidgetScreenDetailsActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(WidgetScreenDetailsActivity.class);

    private static final int SLOT_UNIT_HEIGHT_DP = 72;
    private static final int SLOT_MARGIN_DP = 4;

    private ActivityWidgetScreenDetailsBinding binding;

    private WidgetScreen widgetScreen;
    private WidgetManager widgetManager;
    private List<WidgetLayout> supportedLayouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWidgetScreenDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final GBDevice device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            LOG.error("device must not be null");
            finish();
            return;
        }

        widgetScreen = (WidgetScreen) getIntent().getSerializableExtra(WidgetScreen.EXTRA_WIDGET_SCREEN);
        if (widgetScreen == null) {
            GB.toast("No widget screen provided to WidgetScreenDetailsActivity", Toast.LENGTH_LONG, GB.ERROR);
            finish();
            return;
        }

        widgetManager = device.getDeviceCoordinator().getWidgetManager(device);

        binding.fabSave.setOnClickListener(view -> {
            for (final WidgetPart part : widgetScreen.getParts()) {
                if (part.getId() == null) {
                    GB.toast(getString(R.string.widget_missing_parts), Toast.LENGTH_LONG, GB.WARN);
                    return;
                }
            }

            updateWidgetScreen();
            WidgetScreenDetailsActivity.this.setResult(Activity.RESULT_OK);
            finish();
        });

        supportedLayouts = widgetManager.getSupportedWidgetLayouts();
        final String[] layoutStrings = new String[supportedLayouts.size()];
        for (int i = 0; i < supportedLayouts.size(); i++) {
            layoutStrings[i] = getString(supportedLayouts.get(i).getName());
        }
        binding.widgetLayoutSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, layoutStrings));
        binding.widgetLayoutSpinner.setOnItemClickListener((parent, view, position, id) -> {
            final WidgetLayout selectedLayout = supportedLayouts.get(position);
            if (widgetScreen.getLayout() != selectedLayout) {
                final ArrayList<WidgetPart> defaultParts = new ArrayList<>();
                for (final WidgetType widgetType : selectedLayout.getWidgetTypes()) {
                    defaultParts.add(new WidgetPart(null, "", widgetType));
                }
                widgetScreen.setParts(defaultParts);
                widgetScreen.setLayout(selectedLayout);
            }
            updateUiFromWidget();
        });

        updateUiFromWidget();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // back button
            // TODO confirm when exiting without saving
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWidgetScreen() {
        widgetManager.saveScreen(widgetScreen);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle state) {
        super.onSaveInstanceState(state);
        state.putSerializable("widgetScreen", widgetScreen);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        widgetScreen = (WidgetScreen) savedInstanceState.getSerializable("widgetScreen");
        updateUiFromWidget();
    }

    private void updateUiFromWidget() {
        // false, so that the adapter does not filter the list to the current layout
        binding.widgetLayoutSpinner.setText(getString(widgetScreen.getLayout().getName()), false);

        binding.widgetSlots.removeAllViews();
        for (final int[] row : getRows(widgetScreen.getLayout())) {
            final LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            for (final int partIdx : row) {
                if (partIdx < widgetScreen.getParts().size()) {
                    rowView.addView(createSlot(rowView, partIdx));
                }
            }
            binding.widgetSlots.addView(rowView);
        }
    }

    /**
     * Returns the indexes of the widget parts in each row of the screen, from top to bottom.
     */
    private static int[][] getRows(final WidgetLayout layout) {
        switch (layout) {
            case TOP_1_BOT_2:
            case TOP_2X2_BOT_2:
                return new int[][]{{0}, {1, 2}};
            case TOP_2_BOT_1:
            case TOP_2_BOT_2X2:
                return new int[][]{{0, 1}, {2}};
            case TOP_2_BOT_2:
                return new int[][]{{0, 1}, {2, 3}};
            case ONE_BY_TWO_SINGLE:
            case TWO_BY_TWO_SINGLE:
            case TWO_BY_THREE_SINGLE:
                return new int[][]{{0}};
            case TWO:
            case TOP_1_BOT_2X2:
            case TOP_2X2_BOT_1:
                return new int[][]{{0}, {1}};
            default:
                throw new IllegalStateException("Unknown layout " + layout);
        }
    }

    private static int getHeightUnits(final WidgetType type) {
        switch (type) {
            case TALL:
            case LARGE:
                return 2;
            case PORTRAIT_LARGE:
                return 3;
            case SMALL:
            case WIDE:
            default:
                return 1;
        }
    }

    private View createSlot(final LinearLayout rowView, final int partIdx) {
        final WidgetPart widgetPart = widgetScreen.getParts().get(partIdx);
        final ItemWidgetScreenSlotBinding slot = ItemWidgetScreenSlotBinding.inflate(getLayoutInflater(), rowView, false);

        final float density = getResources().getDisplayMetrics().density;
        final int units = getHeightUnits(widgetPart.getType());
        final int heightDp = units * SLOT_UNIT_HEIGHT_DP + (units - 1) * 2 * SLOT_MARGIN_DP;
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) slot.getRoot().getLayoutParams();
        params.height = Math.round(heightDp * density);
        params.weight = 1;
        slot.getRoot().setLayoutParams(params);

        if (widgetPart.getId() == null) {
            slot.widgetSlotName.setText(R.string.not_set);
            slot.widgetSlotName.setTextColor(MaterialColors.getColor(slot.widgetSlotName, com.google.android.material.R.attr.colorOnSurfaceVariant));
        } else {
            slot.widgetSlotName.setText(widgetPart.getFullName());
        }

        slot.getRoot().setOnClickListener(view -> showPartPicker(partIdx));

        return slot.getRoot();
    }

    private void showPartPicker(final int partIdx) {
        final WidgetPart currentPart = widgetScreen.getParts().get(partIdx);
        final List<WidgetPart> supportedParts = widgetManager.getSupportedWidgetParts(currentPart.getType());
        final String[] partStrings = new String[supportedParts.size()];
        int checkedPart = -1;
        for (int i = 0; i < supportedParts.size(); i++) {
            partStrings[i] = supportedParts.get(i).getName();
            if (currentPart.getId() != null && currentPart.getId().equals(supportedParts.get(i).getId())) {
                checkedPart = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget)
            .setSingleChoiceItems(partStrings, checkedPart, (dialog, i) -> {
                dialog.dismiss();
                final WidgetPart selectedPart = supportedParts.get(i);

                final List<WidgetPartSubtype> supportedSubtypes = selectedPart.getSupportedSubtypes();
                if (supportedSubtypes.isEmpty()) {
                    widgetScreen.getParts().set(partIdx, selectedPart);
                    updateUiFromWidget();
                    return;
                }

                // If the selected part supports subtypes, the user must select a subtype
                showSubtypePicker(partIdx, currentPart, selectedPart);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showSubtypePicker(final int partIdx, final WidgetPart currentPart, final WidgetPart selectedPart) {
        final List<WidgetPartSubtype> supportedSubtypes = selectedPart.getSupportedSubtypes();
        final String[] subtypeStrings = new String[supportedSubtypes.size()];
        int checkedSubtype = -1;
        for (int j = 0; j < supportedSubtypes.size(); j++) {
            subtypeStrings[j] = supportedSubtypes.get(j).getName();
            if (Objects.equals(currentPart.getId(), selectedPart.getId()) && currentPart.getSubtype() != null &&
                Objects.equals(currentPart.getSubtype().getId(), supportedSubtypes.get(j).getId())) {
                checkedSubtype = j;
            }
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_subtype)
            .setSingleChoiceItems(subtypeStrings, checkedSubtype, (dialog, j) -> {
                dialog.dismiss();
                selectedPart.setSubtype(supportedSubtypes.get(j));
                widgetScreen.getParts().set(partIdx, selectedPart);
                updateUiFromWidget();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
