package nodomain.freeyourgadget.gadgetbridge.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

/**
 * Adapter for a searchable list of all the available time zones.
 */
public class TimeZonePickerAdapter extends RecyclerView.Adapter<TimeZonePickerAdapter.ViewHolder> {
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();
    private final String selectedId;
    private final Consumer<String> onSelected;
    private String normalizedQuery = "";

    public TimeZonePickerAdapter(final String selectedId, final Consumer<String> onSelected) {
        this.selectedId = selectedId;
        this.onSelected = onSelected;
    }

    public static List<Entry> buildEntries() {
        final List<Entry> entries = new ArrayList<>();
        final Date now = new Date();
        final Locale locale = Locale.getDefault();
        for (final String id : TimeZone.getAvailableIDs()) {
            final TimeZone timeZone = TimeZone.getTimeZone(id);
            final String[] idParts = id.split("/");
            final String name = idParts[idParts.length - 1].replace('_', ' ');
            final String displayName = timeZone.getDisplayName(timeZone.inDaylightTime(now), TimeZone.LONG, locale);
            final String searchText = (id.replace('_', ' ') + " " + displayName).toLowerCase(locale);
            entries.add(new Entry(id, name, DateTimeUtils.formatUtcOffset(timeZone, now.getTime()), searchText));
        }
        return entries;
    }

    public void setEntries(final List<Entry> entries) {
        allEntries.clear();
        allEntries.addAll(entries);
        applyFilter();
    }

    public void filter(final String query) {
        normalizedQuery = query.trim().toLowerCase(Locale.getDefault());
        applyFilter();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFilter() {
        visibleEntries.clear();
        for (final Entry entry : allEntries) {
            if (entry.searchText.contains(normalizedQuery)) {
                visibleEntries.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        for (int i = 0; i < visibleEntries.size(); i++) {
            if (visibleEntries.get(i).id.equals(selectedId)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timezone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final Entry entry = visibleEntries.get(position);
        holder.name.setText(entry.name);
        holder.id.setText(holder.itemView.getContext().getString(R.string.activity_list_row_stats_pair, entry.id, entry.offset));
        holder.radio.setChecked(entry.id.equals(selectedId));
        holder.itemView.setOnClickListener(v -> onSelected.accept(entry.id));
    }

    @Override
    public int getItemCount() {
        return visibleEntries.size();
    }

    public record Entry(String id, String name, String offset, String searchText) {
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView id;
        final RadioButton radio;

        ViewHolder(final View view) {
            super(view);
            name = view.findViewById(R.id.timezone_item_name);
            id = view.findViewById(R.id.timezone_item_id);
            radio = view.findViewById(R.id.timezone_item_radio);
        }
    }
}
