/*  Copyright (C) 2016-2026 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Damien Gaignon, Daniel Dakhno, Daniele Gobbetti, Davis Mosenkovs,
    fparri, José Rebelo, mamucho, maxirnilian, mkusnierz, Petr Vaněk, Taavi
    Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.adapter;

import static nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_CONNECT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.DeviceDeleteActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2;
import nodomain.freeyourgadget.gadgetbridge.activities.OpenFwAppInstallerActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityChartsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.ActivityGauge;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager;
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardLayout;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceFolder;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

/**
 * Adapter for displaying GBDevice instances.
 */
public class GBDeviceAdapterv2 extends ListAdapter<GBDevice, GBDeviceAdapterv2.ViewHolder> {
    private static final Logger LOG = LoggerFactory.getLogger(GBDeviceAdapterv2.class);

    private static final String STATE_EXPANDED_DEVICE = "devices_expanded_device";
    private static final String STATE_EXPANDED_FOLDER = "devices_expanded_folder";
    private static final String STATE_REVEALED_CARD_ITEMS = "devices_revealed_card_items";

    private final Context context;
    private final GaugeDrawer gaugeDrawer = new GaugeDrawer();
    private List<GBDevice> deviceList;
    private List<GBDevice> devicesListWithFolders;
    private String expandedDeviceAddress = "";
    private final Set<String> revealedCardItemsAddresses = new HashSet<>();
    private String pendingCompanionAddress = null;
    private String expandedFolderName = "";
    private ViewGroup parent;
    private HashMap<String, DailyTotals> deviceActivityMap = new HashMap<>();
    private final StableIdGenerator idGenerator = new StableIdGenerator();

    public GBDeviceAdapterv2(Context context, List<GBDevice> deviceList, HashMap<String, DailyTotals> deviceMap) {
        super(new GBDeviceDiffUtil());
        this.context = context;
        this.deviceList = deviceList;
        rebuildFolders();
        this.deviceActivityMap = deviceMap;
    }

    public void saveInstanceState(final Bundle outState) {
        outState.putString(STATE_EXPANDED_DEVICE, expandedDeviceAddress);
        outState.putString(STATE_EXPANDED_FOLDER, expandedFolderName);
        outState.putStringArrayList(STATE_REVEALED_CARD_ITEMS, new ArrayList<>(revealedCardItemsAddresses));
    }

    public void restoreInstanceState(final Bundle savedInstanceState) {
        expandedDeviceAddress = savedInstanceState.getString(STATE_EXPANDED_DEVICE, "");
        expandedFolderName = savedInstanceState.getString(STATE_EXPANDED_FOLDER, "");
        revealedCardItemsAddresses.clear();
        final ArrayList<String> revealed = savedInstanceState.getStringArrayList(STATE_REVEALED_CARD_ITEMS);
        if (revealed != null) {
            revealedCardItemsAddresses.addAll(revealed);
        }
        rebuildFolders();
    }

    public void rebuildFolders() {
        this.devicesListWithFolders = enrichDeviceListWithFolder(deviceList);
    }

    @SuppressLint("NotifyDataSetChanged")
    public final void refreshSingleDevice(final GBDevice device) {
        final int i = devicesListWithFolders.indexOf(device);
        if (i >= 0) {
            notifyItemChanged(i);
        } else {
            // Somehow the device was not on the list - rebuild everything
            rebuildFolders();
            notifyDataSetChanged();
        }
    }

    private List<GBDevice> enrichDeviceListWithFolder(List<GBDevice> deviceList) {
        final Map<String, List<GBDevice>> devicesPerFolder = new LinkedHashMap<>();
        final List<GBDevice> enrichedList = new ArrayList<>();

        for (GBDevice device : deviceList) {
            String folder = device.getParentFolder();
            if (StringUtils.isNullOrEmpty(folder)) {
                enrichedList.add(device);
                continue;
            }
            if (!devicesPerFolder.containsKey(folder)) {
                devicesPerFolder.put(folder, new ArrayList<>());
            }
            devicesPerFolder.get(folder).add(device);
        }

        for (final Map.Entry<String, List<GBDevice>> folder : devicesPerFolder.entrySet()) {
            enrichedList.add(new GBDeviceFolder(folder.getKey()));
            if (folder.getKey().equals(expandedFolderName)) {
                enrichedList.addAll(folder.getValue());
            }
        }

        return enrichedList;
    }

    @NonNull
    @Override
    public GBDeviceAdapterv2.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.parent = parent;
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_itemv2, parent, false);
        return new ViewHolder(view);
    }

    private int countDevicesInFolder(String folderName, boolean needsToBeConnected) {
        int count = 0;
        for (GBDevice device : deviceList) {
            if (folderName.equals(device.getParentFolder()) && ((!needsToBeConnected) || device.isConnected())) {
                count++;
            }
        }
        return count;
    }

    private static void setDeviceIcon(final ViewHolder holder, @DrawableRes final int iconRes, final boolean connected) {
        final Context iconContext = connected ? holder.connectedIconContext : holder.deviceImageView.getContext();
        holder.deviceImageView.setImageDrawable(AppCompatResources.getDrawable(iconContext, iconRes));
        holder.deviceImageView.setBackground(AppCompatResources.getDrawable(iconContext, R.drawable.device_card_icon_bg));
        if (connected) {
            holder.deviceImageView.setColorFilter(null);
        } else {
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            holder.deviceImageView.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        }
    }

    private void showDeviceFolder(ViewHolder holder, final GBDeviceFolder folder) {
        holder.container.setVisibility(View.VISIBLE);
        holder.deviceNameLabel.setText(folder.getName());
        holder.infoIcons.setVisibility(View.GONE);
        holder.quickControls.setVisibility(View.GONE);
        holder.companionHint.setVisibility(View.GONE);
        holder.deviceImageView.setOnLongClickListener(null);
        holder.deviceInfoBox.setVisibility(View.GONE);
        holder.cardViewActivityCardLayout.setVisibility(View.GONE);
        setDeviceIcon(holder, R.drawable.ic_device_folder, countDevicesInFolder(folder.getName(), true) > 0);
        holder.deviceInfoView.setVisibility(View.GONE);
        int countInFolder = countDevicesInFolder(folder.getName(), false);
        int connectedInFolder = countDevicesInFolder(folder.getName(), true);
        holder.deviceStatusLabel.setText(context.getString(R.string.controlcenter_connected_fraction, connectedInFolder, countInFolder));
        DeviceStatusDot.apply(holder.deviceStatusDot, connectedInFolder > 0);

        final View.OnClickListener toggleFolder = v -> {
            if (expandedFolderName.equals(folder.getName())) {
                // collapse open folder
                expandedFolderName = "";
            } else {
                expandedFolderName = folder.getName();
            }
            rebuildFolders();
            notifyDataSetChanged();
        };
        holder.container.setOnClickListener(toggleFolder);
        holder.deviceImageView.setOnClickListener(toggleFolder);
        holder.container.setOnLongClickListener(null);
    }

    private void setItemMargin(ViewHolder holder, GBDevice device) {
        Resources r = context.getResources();
        int widthDp = 8;
        if (!StringUtils.isNullOrEmpty(device.getParentFolder())) {
            widthDp = 20;
        }
        float px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthDp,
            r.getDisplayMetrics()
        );
        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) holder.container.getLayoutParams();
        layoutParams.setMarginStart((int) px);
        holder.container.setLayoutParams(layoutParams);

        int alpha = 0;
        if (device instanceof GBDeviceFolder && device.getName().equals(expandedFolderName)) {
            alpha = 50;
        } else if (!StringUtils.isNullOrEmpty(device.getParentFolder()) && expandedFolderName.equals(device.getParentFolder())) {
            alpha = 50;
        }

        holder.root.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
    }

    void handleDeviceConnect(GBDevice device) {
        if (!device.getDeviceCoordinator().isConnectable()) {
            device.setState(GBDevice.State.WAITING_FOR_SCAN);
            device.sendDeviceUpdateIntent(GBApplication.getContext(), GBDevice.DeviceUpdateSubject.CONNECTION_STATE);
            return;
        }

        GBApplication.deviceService(device).connect();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        final GBDevice device = devicesListWithFolders.get(position);

        setItemMargin(holder, device);

        if (device instanceof GBDeviceFolder) {
            showDeviceFolder(holder, (GBDeviceFolder) device);
            return;
        }

        String parentFolder = device.getParentFolder();
        if (!StringUtils.isNullOrEmpty(parentFolder)) {
            if (parentFolder.equals(expandedFolderName)) {
                holder.container.setVisibility(View.VISIBLE);
            } else {
                holder.container.setVisibility(View.GONE);
            }
        } else {
            holder.container.setVisibility(View.VISIBLE);
        }

        DailyTotals dailyTotals = new DailyTotals();
        if (deviceActivityMap.containsKey(device.getAddress())) {
            dailyTotals = deviceActivityMap.get(device.getAddress());
        }

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        holder.container.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (device.isInitialized() || device.isConnected()) {
                    showTransientSnackbar(R.string.controlcenter_snackbar_need_longpress);
                } else {
                    showTransientSnackbar(R.string.controlcenter_snackbar_connecting);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        createDynamicShortcut(device);
                    }
                    handleDeviceConnect(device);
                }
            }
        });

        holder.container.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showDeviceSubmenu(v, device);
                return true;
            }
        });

        setDeviceIcon(holder, device.getDeviceCoordinator().getDefaultIconResource(), device.isInitialized());

        holder.deviceNameLabel.setText(getUniqueDeviceName(device));

        if (device.isBusy()) {
            holder.deviceStatusLabel.setText(device.getBusyTask());
        } else {
            holder.deviceStatusLabel.setText(device.getStateString(context));
        }
        DeviceStatusDot.apply(holder.deviceStatusDot, device);

        //begin of action row: batteries, presets, status values and custom actions are all rendered
        //dynamically from the coordinator's declared card items, see DeviceCardItemBinder
        String hrSampleText = null;
        if (parent.getContext() instanceof ControlCenterv2) {
            ActivitySample sample = ((ControlCenterv2) parent.getContext()).getCurrentHRSample(device);
            if (sample != null) {
                hrSampleText = String.valueOf(sample.getHeartRate());
            }
        }
        DeviceCardItemBinder.bind(
            holder.infoIcons,
            DeviceCardLayout.ITEMS.apply(device, coordinator.getCardItems(device)),
            device,
            context,
            hrSampleText
        );
        QuickControlBinder.bind(
            holder.quickControls,
            DeviceCardLayout.QUICK_CONTROLS.apply(device, coordinator.getQuickControls(device)),
            device,
            context
        );

        bindCompanionHint(holder, device);

        ItemWithDetailsAdapter infoAdapter = new ItemWithDetailsAdapter(context, device.getDeviceInfos());
        infoAdapter.setHorizontalAlignment(true);
        holder.deviceInfoList.setAdapter(infoAdapter);
        justifyListViewHeightBasedOnChildren(holder.deviceInfoList);
        holder.deviceInfoList.setFocusable(false);

        if (DeviceCardLayout.isShown(device)) {
            holder.infoIcons.setVisibility(View.VISIBLE);
            holder.deviceImageView.setOnClickListener(null);
        } else {
            final boolean revealed = revealedCardItemsAddresses.contains(device.getAddress());
            holder.infoIcons.setVisibility(revealed ? View.VISIBLE : View.GONE);
            holder.deviceImageView.setOnClickListener(v -> {
                if (!revealedCardItemsAddresses.remove(device.getAddress())) {
                    revealedCardItemsAddresses.add(device.getAddress());
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        }
        // The card items have a bottom margin. When they are hidden, we need to add a margin to the quick controls.
        final ViewGroup.MarginLayoutParams quickControlsParams = (ViewGroup.MarginLayoutParams) holder.quickControls.getLayoutParams();
        quickControlsParams.topMargin = holder.infoIcons.getVisibility() == View.VISIBLE
            ? 0
            : context.getResources().getDimensionPixelSize(R.dimen.quick_control_spacing);
        holder.quickControls.setLayoutParams(quickControlsParams);
        holder.deviceImageView.setOnLongClickListener(v -> {
            DeviceSettingsActivity.start(context, device);
            return true;
        });

        final boolean detailsShown = expandedDeviceAddress.equals(device.getAddress());
        boolean showInfoIcon = device.hasDeviceInfos() && !device.isBusy();
        holder.deviceInfoBox.setActivated(detailsShown);
        holder.deviceInfoBox.setVisibility(detailsShown ? View.VISIBLE : View.GONE);
        holder.deviceInfoView.setVisibility(View.VISIBLE);
        holder.deviceInfoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeviceSubmenu(v, device);
            }
        });

        holder.cardViewActivityCardLayout.setVisibility(coordinator.supportsActivityTracking(device) ? View.VISIBLE : View.GONE);
        holder.cardViewActivityCardLayout.setMinimumWidth(coordinator.supportsActivityTracking(device) ? View.VISIBLE : View.GONE);

        if (coordinator.supportsActivityTracking(device)) {
            setActivityCard(holder, device, dailyTotals);
        }
    }

    private void bindCompanionHint(final ViewHolder holder, final GBDevice device) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            || !(context instanceof Activity)
            || !CompanionPairingHint.shouldShow(context, device)) {
            holder.companionHint.setVisibility(View.GONE);
            return;
        }
        holder.companionHint.setVisibility(View.VISIBLE);
        holder.companionHintDismiss.setOnClickListener(v -> {
            CompanionPairingHint.dismiss(device);
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
        holder.companionHintPair.setOnClickListener(v -> {
            pendingCompanionAddress = device.getAddress();
            CompanionPairingHint.pair((Activity) context, device);
        });
    }

    public void onResume() {
        if (pendingCompanionAddress == null) {
            return;
        }
        final String address = pendingCompanionAddress;
        pendingCompanionAddress = null;
        CompanionPairingHint.onChooserClosed(context, address);
        for (int i = 0; i < devicesListWithFolders.size(); i++) {
            if (address.equals(devicesListWithFolders.get(i).getAddress())) {
                notifyItemChanged(i);
            }
        }
    }

    private boolean showInstallerItem(GBDevice device) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsAppsManagement(device) || coordinator.supportsFlashing(device);
    }

    private void showDeviceSubmenu(final View v, final GBDevice device) {
        boolean deviceConnected = device.getState() != GBDevice.State.NOT_CONNECTED;
        PopupMenu menu = new PopupMenu(v.getContext(), v);
        menu.inflate(R.menu.fragment_devices_device_submenu);

        final boolean detailsShown = expandedDeviceAddress.equals(device.getAddress());
        boolean showInfoIcon = device.hasDeviceInfos() && !device.isBusy();

        if (BuildConfig.DEBUG) {
            menu.getMenu().findItem(R.id.controlcenter_device_submenu_test_new_function).setVisible(deviceConnected);
        }
        menu.getMenu().findItem(R.id.controlcenter_device_submenu_connect).setVisible(!deviceConnected);
        menu.getMenu().findItem(R.id.controlcenter_device_submenu_disconnect).setVisible(deviceConnected);
        menu.getMenu().findItem(R.id.controlcenter_device_submenu_show_details).setEnabled(showInfoIcon);
        menu.getMenu().findItem(R.id.controlcenter_device_submenu_installer).setEnabled(deviceConnected);
        menu.getMenu().findItem(R.id.controlcenter_device_submenu_installer).setVisible(showInstallerItem(device));

        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                final int itemId = item.getItemId();
                if (itemId == R.id.controlcenter_device_submenu_connect) {
                    if (device.getState() != GBDevice.State.CONNECTED) {
                        showTransientSnackbar(R.string.controlcenter_snackbar_connecting);
                        handleDeviceConnect(device);
                    }
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_disconnect) {
                    if (device.getState() != GBDevice.State.NOT_CONNECTED) {
                        showTransientSnackbar(R.string.controlcenter_snackbar_disconnecting);
                        GBApplication.deviceService(device).disconnect();
                    }
                    removeFromLastDeviceAddressesPref(device);
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_test_new_function) {
                    if (device.isInitialized()) {
                        GBApplication.deviceService(device).onTestNewFunction(null);
                        showTransientSnackbar(R.string.controlcenter_test_new_function);
                    }
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_device_settings) {
                    DeviceSettingsActivity.start(context, device);
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_set_alias) {
                    showSetAliasDialog(device);
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_remove) {
                    showRemoveDeviceDialog(device);
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_show_details) {
                    final String previouslyExpandedDeviceAddress = expandedDeviceAddress;
                    expandedDeviceAddress = detailsShown ? "" : device.getAddress();

                    if (!previouslyExpandedDeviceAddress.isEmpty()) {
                        // Notify the previously expanded device for a change (collapsing it)
                        for (int i = 0; i < devicesListWithFolders.size(); i++) {
                            final GBDevice gbDevice = devicesListWithFolders.get(i);
                            if (gbDevice.getAddress().equals(previouslyExpandedDeviceAddress)) {
                                notifyItemChanged(devicesListWithFolders.indexOf(gbDevice));
                                break;
                            }
                        }
                    }

                    // Update the current one
                    notifyItemChanged(devicesListWithFolders.indexOf(device));
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_set_parent_folder) {
                    showSetParentFolderDialog(device);
                    return true;
                } else if (itemId == R.id.controlcenter_device_submenu_installer) {
                    Intent openFwIntent = new Intent(context, OpenFwAppInstallerActivity.class);
                    openFwIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                    context.startActivity(openFwIntent);
                    return false;
                }

                return false;
            }
        });
        menu.show();
    }

    private void showRemoveDeviceDialog(final GBDevice device) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setCancelable(true)
            .setTitle(context.getString(R.string.controlcenter_delete_device_name, device.getAliasOrName()))
            .setMessage(R.string.controlcenter_delete_device_dialogmessage)
            .setPositiveButton(R.string.delete,
                (dialog, which) -> removeDevice(device, true))
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
            });

        if (deviceHasFiles(device)) {
            builder.setNeutralButton(R.string.delete_device_and_retain_files,
                (dialog, which) -> removeDevice(device, false));
        }
        builder.show();
    }

    private void removeDevice(GBDevice device, boolean deleteFiles) {
        final Intent intent = new Intent(context, DeviceDeleteActivity.class);
        intent.putExtra(DeviceDeleteActivity.EXTRA_DEVICE, device);
        intent.putExtra(DeviceDeleteActivity.EXTRA_DELETE_FILES, deleteFiles);
        context.startActivity(intent);
    }

    private boolean deviceHasFiles(final GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();

        try {
            File cache = coordinator.getAppCacheDir();
            if (cache != null && cache.exists()) {
                return true;
            }
        } catch (Exception e) {
            LOG.warn("failed to check cache dir", e);
            // assume device has cache files
            return true;
        }

        try {
            File export = coordinator.getWritableExportDirectory(device, false);
            if (export != null && export.exists()) {
                return true;
            }
        } catch (Exception e) {
            LOG.warn("failed to check export dir", e);
            // assume device has export files
            return true;
        }

        return false;
    }

    private void showSetParentFolderDialog(final GBDevice device) {
        final String[] selectedFolder = new String[1];

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout newFolderLayout = new LinearLayout(context);
        newFolderLayout.setOrientation(LinearLayout.HORIZONTAL);
        newFolderLayout.setPadding(context.getResources().getDimensionPixelSize(R.dimen.dialog_margin),
            0, context.getResources().getDimensionPixelSize(R.dimen.dialog_margin), 0);

        final TextView newFolderLabel = new TextView(context);
        newFolderLabel.setText(R.string.controlcenter_folder_name);
        final EditText newFolderInput = new EditText(context);
        newFolderInput.setInputType(InputType.TYPE_CLASS_TEXT);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        newFolderInput.setLayoutParams(params);

        newFolderInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                selectedFolder[0] = editable.toString();
            }
        });

        newFolderLayout.addView(newFolderLabel);
        newFolderLayout.addView(newFolderInput);

        final Spinner deviceListSpinner = new Spinner(context);
        ArrayList<SpinnerWithIconItem> foldersList = new ArrayList<>();
        for (GBDevice oneDevice : deviceList) {
            String folder = oneDevice.getParentFolder();
            if (StringUtils.isNullOrEmpty(folder)) {
                continue;
            }
            if (folderListContainsName(foldersList, folder)) {
                continue;
            }
            foldersList.add(new SpinnerWithIconItem(folder, 2L, R.drawable.ic_folder));
        }

        foldersList.add(new SpinnerWithIconItem(context.getString(R.string.controlcenter_add_new_folder), 0L, R.drawable.ic_create_new_folder));
        if (foldersList.size() > 1) {
            foldersList.add(new SpinnerWithIconItem(context.getString(R.string.controlcenter_unset_folder), 1L, R.drawable.ic_folder_delete));
        }

        final SpinnerWithIconAdapter deviceListAdapter = new SpinnerWithIconAdapter((Activity) context,
            R.layout.spinner_with_image_layout, R.id.spinner_item_text, foldersList);
        deviceListSpinner.setAdapter(deviceListAdapter);

        deviceListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                SpinnerWithIconItem selectedItem = (SpinnerWithIconItem) parent.getItemAtPosition(pos);
                int folderId = selectedItem.getId().intValue();
                switch (folderId) {
                    case 0: //Add new folder from test input
                        newFolderLayout.setVisibility(View.VISIBLE);
                        selectedFolder[0] = newFolderInput.getText().toString();
                        break;
                    case 1: //Unset folder
                        newFolderLayout.setVisibility(View.GONE);
                        selectedFolder[0] = "";
                        break;
                    default: //Set folder from selection
                        newFolderLayout.setVisibility(View.GONE);
                        selectedFolder[0] = selectedItem.getText();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
            }
        });

        linearLayout.addView(deviceListSpinner);
        linearLayout.addView(newFolderLayout);

        new MaterialAlertDialogBuilder(context)
            .setCancelable(true)
            .setTitle(R.string.controlcenter_set_folder_title)
            .setView(linearLayout)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    try (DBHandler dbHandler = GBApplication.acquireDB()) {
                        DaoSession session = dbHandler.getDaoSession();
                        Device dbDevice = DBHelper.getDevice(device, session);
                        String parentFolder = selectedFolder[0];
                        dbDevice.setParentFolder(parentFolder);
                        dbDevice.update();
                        device.setParentFolder(parentFolder);
                        expandedFolderName = parentFolder;
                    } catch (Exception ex) {
                        GB.toast(context, context.getString(R.string.error_setting_parent_folder, ex.getLocalizedMessage()), Toast.LENGTH_LONG, GB.ERROR, ex);
                    } finally {
                        Intent refreshIntent = new Intent(DeviceManager.ACTION_REFRESH_DEVICELIST);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(refreshIntent);
                    }
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .show();
    }

    private boolean folderListContainsName(ArrayList<SpinnerWithIconItem> list, String name) {
        for (SpinnerWithIconItem item : list) {
            if (item.getText().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void removeFromLastDeviceAddressesPref(GBDevice device) {
        Set<String> lastDeviceAddresses = GBApplication.getPrefs().getStringSet(GBPrefs.LAST_DEVICE_ADDRESSES, Collections.emptySet());
        if (lastDeviceAddresses.contains(device.getAddress())) {
            lastDeviceAddresses = new HashSet<String>(lastDeviceAddresses);
            lastDeviceAddresses.remove(device.getAddress());
            GBApplication.getPrefs().getPreferences().edit().putStringSet(GBPrefs.LAST_DEVICE_ADDRESSES, lastDeviceAddresses).apply();
        }
    }

    private void setAppPreferences(GBDevice device) {
        Intent startIntent;
        startIntent = new Intent(context, DeviceSettingsActivity.class);
        startIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
        startIntent.putExtra(DeviceSettingsActivity.MENU_ENTRY_POINT, DeviceSettingsActivity.MENU_ENTRY_POINTS.APPLICATION_SETTINGS);
        context.startActivity(startIntent);
    }

    private void showSetAliasDialog(final GBDevice device) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(device.getAlias());
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        input.setLayoutParams(params);
        container.addView(input);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text

        new MaterialAlertDialogBuilder(context)
            .setView(container)
            .setCancelable(true)
            .setTitle(context.getString(R.string.controlcenter_set_alias))
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try (DBHandler dbHandler = GBApplication.acquireDB()) {
                        DaoSession session = dbHandler.getDaoSession();
                        Device dbDevice = DBHelper.getDevice(device, session);
                        String alias = input.getText().toString();
                        dbDevice.setAlias(alias);
                        dbDevice.update();
                        device.setAlias(alias);
                    } catch (Exception ex) {
                        GB.toast(context, context.getString(R.string.error_setting_alias) + ex.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR, ex);
                    } finally {
                        Intent refreshIntent = new Intent(DeviceManager.ACTION_REFRESH_DEVICELIST);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(refreshIntent);
                    }
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // do nothing
                }
            })
            .show();
    }

    @Override
    public int getItemCount() {
        return devicesListWithFolders.size();
    }

    @Override
    public long getItemId(int position) {
        return idGenerator.getId(devicesListWithFolders.get(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        View root;
        MaterialCardView container;

        ImageView deviceImageView;
        final Context connectedIconContext;
        TextView deviceNameLabel;
        TextView deviceStatusLabel;
        View deviceStatusDot;

        // Icon row: batteries, presets, status items and custom actions are all rendered
        // dynamically into this container by DeviceCardItemBinder.
        FlexboxLayout infoIcons;

        LinearLayout quickControls;

        final LinearLayout companionHint;
        final Button companionHintDismiss;
        final Button companionHintPair;

        ImageView deviceInfoView;
        //overflow
        final RelativeLayout deviceInfoBox;
        ListView deviceInfoList;

        //activity card
        LinearLayout cardViewActivityCardLayout;
        ActivityGauge TotalStepsChart;
        ActivityGauge TotalDistanceChart;
        ActivityGauge SleepTimeChart;

        ViewHolder(View view) {
            super(view);

            root = view;

            container = view.findViewById(R.id.card_view);

            deviceImageView = view.findViewById(R.id.device_image);
            connectedIconContext = new ContextThemeWrapper(deviceImageView.getContext(), R.style.ThemeOverlay_App_DeviceCardIcon_Connected);
            deviceNameLabel = view.findViewById(R.id.device_name);
            deviceStatusLabel = view.findViewById(R.id.device_status);
            deviceStatusDot = view.findViewById(R.id.device_status_dot);

            deviceInfoView = view.findViewById(R.id.device_info_image);

            deviceInfoBox = view.findViewById(R.id.device_item_infos_box);
            //overflow
            deviceInfoList = view.findViewById(R.id.device_item_infos);
            infoIcons = view.findViewById(R.id.device_info_icons);
            quickControls = view.findViewById(R.id.device_quick_controls);

            companionHint = view.findViewById(R.id.companion_hint);
            companionHintDismiss = view.findViewById(R.id.companion_hint_dismiss);
            companionHintPair = view.findViewById(R.id.companion_hint_pair);

            cardViewActivityCardLayout = view.findViewById(R.id.card_view_activity_card_layout);

            TotalStepsChart = new ActivityGauge(view.findViewById(R.id.device_card_gauge_steps));
            TotalDistanceChart = new ActivityGauge(view.findViewById(R.id.device_card_gauge_distance));
            SleepTimeChart = new ActivityGauge(view.findViewById(R.id.device_card_gauge_sleep));
        }
    }

    private void justifyListViewHeightBasedOnChildren(ListView listView) {
        ArrayAdapter adapter = (ArrayAdapter) listView.getAdapter();

        if (adapter == null) {
            return;
        }
        int totalHeight = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            View listItem = adapter.getView(i, null, listView);
            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams par = listView.getLayoutParams();
        par.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
        listView.setLayoutParams(par);
        listView.requestLayout();
    }

    private String getUniqueDeviceName(GBDevice device) {
        String deviceName = device.getAliasOrName();

        if (!isUniqueDeviceName(device, deviceName)) {
            if (device.getModel() != null) {
                deviceName = deviceName + " " + device.getModel();
                if (!isUniqueDeviceName(device, deviceName)) {
                    deviceName = deviceName + " " + device.getShortAddress();
                }
            } else {
                deviceName = deviceName + " " + device.getShortAddress();
            }
        }
        return deviceName;
    }

    private boolean isUniqueDeviceName(GBDevice device, String deviceName) {
        for (int i = 0; i < deviceList.size(); i++) {
            GBDevice item = deviceList.get(i);
            if (item == device) {
                continue;
            }
            if (deviceName.equals(item.getName())) {
                return false;
            }
        }
        return true;
    }

    private void showTransientSnackbar(int resource) {
        Snackbar snackbar = Snackbar.make(parent, resource, Snackbar.LENGTH_SHORT);

        //View snackbarView = snackbar.getView();

        // change snackbar text color
        //int snackbarTextId = android.support.design.R.id.snackbar_text;
        //TextView textView = snackbarView.findViewById(snackbarTextId);
        //textView.setTextColor();
        //snackbarView.setBackgroundColor(Color.MAGENTA);
        snackbar.show();
    }

    private void setActivityCard(ViewHolder holder, final GBDevice device, DailyTotals dailyTotals) {
        boolean showActivityCard = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREFS_ACTIVITY_IN_DEVICE_CARD, true);
        holder.cardViewActivityCardLayout.setVisibility(showActivityCard ? View.VISIBLE : View.GONE);

        if (!showActivityCard) {
            return;
        }

        int steps = (int) dailyTotals.getSteps();
        int sleep = (int) dailyTotals.getSleep();
        int distanceCm = (int) dailyTotals.getDistance();
        ActivityUser activityUser = new ActivityUser();
        int stepGoal = activityUser.getStepsGoal();
        int sleepGoalMinutes = activityUser.getSleepDurationGoal();
        int distanceGoal = activityUser.getDistanceGoalMeters() * 100;
        int stepLength = activityUser.getStepLengthCm();
        int distanceForChart = distanceCm > 0 ? distanceCm : steps * stepLength;
        double distanceMeters = (distanceCm > 0 ? distanceCm : steps * stepLength) * 0.01;
        String distanceFormatted = FormatUtils.getFormattedDistanceLabel(distanceMeters);

        holder.TotalStepsChart.update(gaugeDrawer, steps, stepGoal, NumberFormat.getInstance().format(steps), context.getString(R.string.steps));

        holder.TotalDistanceChart.update(gaugeDrawer, distanceForChart, distanceGoal, distanceFormatted, context.getString(R.string.distance));

        holder.SleepTimeChart.update(gaugeDrawer, sleep, sleepGoalMinutes, String.format("%1s", getHM(sleep)), context.getString(R.string.prefs_activity_in_device_card_sleep_title));


        boolean showActivitySteps = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREFS_ACTIVITY_IN_DEVICE_CARD_STEPS, true);
        boolean showActivitySleep = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREFS_ACTIVITY_IN_DEVICE_CARD_SLEEP, true);
        boolean showActivityDistance = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREFS_ACTIVITY_IN_DEVICE_CARD_DISTANCE, true);

        //do the multiple mini-charts for activities in a loop
        Hashtable<View, Pair<Boolean, Integer>> activitiesStatusMiniCharts = new Hashtable<>();
        activitiesStatusMiniCharts.put(holder.TotalStepsChart.container, new Pair<>(showActivitySteps && steps > 0, ActivityChartsActivity.getChartsTabIndex("stepsweek", device, context)));
        activitiesStatusMiniCharts.put(holder.SleepTimeChart.container, new Pair<>(showActivitySleep && sleep > 0, ActivityChartsActivity.getChartsTabIndex("sleep", device, context)));
        activitiesStatusMiniCharts.put(holder.TotalDistanceChart.container, new Pair<>(showActivityDistance && distanceForChart > 0, ActivityChartsActivity.getChartsTabIndex("activity", device, context)));

        for (Map.Entry<View, Pair<Boolean, Integer>> miniCharts : activitiesStatusMiniCharts.entrySet()) {
            View miniChart = miniCharts.getKey();
            final Pair<Boolean, Integer> parameters = miniCharts.getValue();
            miniChart.setVisibility(parameters.first ? View.VISIBLE : View.GONE);
            miniChart.setOnClickListener(new View.OnClickListener() {
                                             @Override
                                             public void onClick(View v) {
                                                 Intent startIntent;
                                                 startIntent = new Intent(context, ActivityChartsActivity.class);
                                                 startIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                                                 startIntent.putExtra(ActivityChartsActivity.EXTRA_FRAGMENT_ID, parameters.second);
                                                 context.startActivity(startIntent);
                                             }
                                         }
            );
        }
    }

    private String getHM(long value) {
        return DateTimeUtils.formatDurationHoursMinutes(value, TimeUnit.MINUTES);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void createDynamicShortcut(GBDevice device) {
        Intent intent = new Intent(context, ControlCenterv2.class)
            .setAction(ACTION_CONNECT)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("device", device.getAddress());

        ShortcutManager shortcutManager = (ShortcutManager) context.getApplicationContext().getSystemService(Context.SHORTCUT_SERVICE);

        DeviceCoordinator coordinator = device.getDeviceCoordinator();

        shortcutManager.pushDynamicShortcut(new ShortcutInfo.Builder(context, device.getAddress())
            .setLongLived(false)
            .setShortLabel(device.getAliasOrName())
            .setIntent(intent)
            .setIcon(Icon.createWithResource(context, coordinator.getDefaultIconResource()))
            .build()
        );
    }

    /**
     * A generator of stable IDs, given a string, since hashCode can easily have collisions.
     */
    private static class StableIdGenerator {
        private final Map<String, Long> idMapping = new HashMap<String, Long>();

        private long nextId = 0;

        public long getId(final GBDevice device) {
            final String str = String.format("%s_%s", device.getAddress(), device.getName());

            if (!idMapping.containsKey(str)) {
                idMapping.put(str, nextId++);
            }

            return idMapping.get(str);
        }
    }
}
