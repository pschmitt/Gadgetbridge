/*  Copyright (C) 2022-2024 Arjan Schrijver, José Rebelo

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


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureWorldClocks;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

/**
 * Adapter for displaying WorldClock instances.
 */
public class GBWorldClockListAdapter extends RecyclerView.Adapter<GBWorldClockListAdapter.ViewHolder> {

    private final Context mContext;
    private final boolean supportsDisabledWorldClocks;
    private ArrayList<WorldClock> worldClockList;

    public GBWorldClockListAdapter(final Context context, final boolean supportsDisabledWorldClocks) {
        this.mContext = context;
        this.supportsDisabledWorldClocks = supportsDisabledWorldClocks;
    }

    public void setWorldClockList(final List<WorldClock> worldClocks) {
        this.worldClockList = new ArrayList<>(worldClocks);
    }

    public ArrayList<WorldClock> getWorldClockList() {
        return worldClockList;
    }

    @NonNull
    @Override
    public GBWorldClockListAdapter.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_world_clock, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final WorldClock worldClock = worldClockList.get(position);
        final boolean enabled = worldClock.getEnabled() == null || worldClock.getEnabled();

        holder.container.setOnClickListener(v -> ((ConfigureWorldClocks) mContext).configureWorldClock(worldClock, false));

        holder.delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
            .setTitle(v.getContext().getString(R.string.world_clock_delete_confirm_title, worldClock.getLabel()))
            .setMessage(R.string.world_clock_delete_confirm_description)
            .setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.delete, (dialog, which) -> ((ConfigureWorldClocks) mContext).deleteWorldClock(worldClock))
            .setNegativeButton(android.R.string.cancel, null)
            .show());

        holder.isEnabled.setVisibility(supportsDisabledWorldClocks ? View.VISIBLE : View.GONE);
        // A recycled view still has the listener of its previous world clock
        holder.isEnabled.setOnCheckedChangeListener(null);
        holder.isEnabled.setChecked(enabled);
        holder.isEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            worldClock.setEnabled(isChecked);
            DBHelper.store(worldClock);
            holder.content.setAlpha(isChecked ? 1.0f : 0.5f);
            ((ConfigureWorldClocks) mContext).sendWorldClocksToDevice();
        });
        holder.content.setAlpha(enabled || !supportsDisabledWorldClocks ? 1.0f : 0.5f);

        final TimeZone timeZone = TimeZone.getTimeZone(worldClock.getTimeZoneId());
        final long now = System.currentTimeMillis();
        final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(mContext);
        timeFormat.setTimeZone(timeZone);
        holder.worldClockCurrentTime.setText(timeFormat.format(new Date(now)));

        if (StringUtils.isNullOrEmpty(worldClock.getCode())) {
            holder.worldClockLabel.setText(worldClock.getLabel());
        } else {
            holder.worldClockLabel.setText(mContext.getString(R.string.activity_list_row_stats_pair, worldClock.getLabel(), worldClock.getCode()));
        }
        holder.worldClockTimezone.setText(mContext.getString(
            R.string.activity_list_row_stats_pair,
            worldClock.getTimeZoneId(),
            DateTimeUtils.formatUtcOffset(timeZone, now)
        ));
    }

    @Override
    public int getItemCount() {
        return worldClockList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView container;
        final View content;

        final TextView worldClockTimezone;
        final TextView worldClockLabel;
        final TextView worldClockCurrentTime;
        final ImageButton delete;
        final MaterialSwitch isEnabled;

        ViewHolder(final View view) {
            super(view);

            container = view.findViewById(R.id.card_view);
            content = view.findViewById(R.id.world_clock_item_content);

            worldClockTimezone = view.findViewById(R.id.world_clock_item_timezone);
            worldClockLabel = view.findViewById(R.id.world_clock_item_label);
            worldClockCurrentTime = view.findViewById(R.id.world_clock_current_time);
            delete = view.findViewById(R.id.world_clock_item_delete);
            isEnabled = view.findViewById(R.id.world_clock_item_toggle);
        }
    }
}
