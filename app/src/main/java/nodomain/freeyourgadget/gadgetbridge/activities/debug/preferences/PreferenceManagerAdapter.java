/*  Copyright (C) 2023-2024 akasaka / Genjitsu Labs

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
package nodomain.freeyourgadget.gadgetbridge.activities.debug.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import nodomain.freeyourgadget.gadgetbridge.R;

public class PreferenceManagerAdapter extends RecyclerView.Adapter<PreferenceManagerAdapter.DebugPreferenceViewHolder> {
    private final Context mContext;
    private final Consumer<DebugPreference> onClick;

    private final List<DebugPreference> allPreferences = new ArrayList<>();
    private final List<DebugPreference> shownPreferences = new ArrayList<>();
    private String filter = "";

    public PreferenceManagerAdapter(final Context context, final Consumer<DebugPreference> onClick) {
        this.mContext = context;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public DebugPreferenceViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(mContext).inflate(R.layout.item_debug_preference, parent, false);
        return new DebugPreferenceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final DebugPreferenceViewHolder holder, final int position) {
        final DebugPreference preference = shownPreferences.get(position);

        holder.name.setText(preference.key());
        holder.description.setText(String.valueOf(preference.value()));
        holder.description.setMaxLines(10);
        holder.menu.setVisibility(View.INVISIBLE);
        holder.itemView.setOnClickListener(v -> onClick.accept(preference));
    }

    @Override
    public int getItemCount() {
        return shownPreferences.size();
    }

    public void setPreferences(final List<DebugPreference> preferences) {
        allPreferences.clear();
        allPreferences.addAll(preferences);
        applyFilter();
    }

    public void setFilter(final String filter) {
        this.filter = filter.toLowerCase(Locale.ROOT).trim();
        applyFilter();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFilter() {
        shownPreferences.clear();
        for (final DebugPreference p : allPreferences) {
            if (filter.isEmpty()
                || p.key().toLowerCase(Locale.ROOT).contains(filter)
                || String.valueOf(p.value()).toLowerCase(Locale.ROOT).contains(filter)) {
                shownPreferences.add(p);
            }
        }
        notifyDataSetChanged();
    }

    public static class DebugPreferenceViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final ImageView menu;

        DebugPreferenceViewHolder(final View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.preference_key);
            description = itemView.findViewById(R.id.preference_description);
            menu = itemView.findViewById(R.id.preference_menu);
        }
    }
}
