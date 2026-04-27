package com.xiaomao.player;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SourceManageAdapter extends RecyclerView.Adapter<SourceManageAdapter.SourceViewHolder> {
    public interface Listener {
        void onSelect(SourceStore.SourceItem item);
        void onTest(SourceStore.SourceItem item);
        void onDebug(SourceStore.SourceItem item);
        void onDelete(SourceStore.SourceItem item);
    }

    private final ArrayList<SourceStore.SourceItem> items = new ArrayList<>();
    private String selectedId = "";
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<SourceStore.SourceItem> sourceItems, String activeSourceId) {
        items.clear();
        if (sourceItems != null) {
            items.addAll(sourceItems);
        }
        selectedId = activeSourceId == null ? "" : activeSourceId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source_manage, parent, false);
        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        SourceStore.SourceItem item = items.get(position);
        boolean selected = item.id.equals(selectedId);
        holder.titleView.setText(item.title);
        holder.hostView.setText(item.host.isEmpty()
                ? holder.itemView.getContext().getString(R.string.source_manage_host_missing)
                : item.host);
        holder.typeView.setText(holder.itemView.getContext().getString(
                item.custom ? R.string.source_manage_type_custom : R.string.source_manage_type_builtin
        ));
        holder.selectButton.setText(holder.itemView.getContext().getString(
                selected ? R.string.source_manage_select_current : R.string.source_manage_select_action
        ));
        holder.selectButton.setEnabled(!selected);
        int accent = ContextCompat.getColor(holder.itemView.getContext(), R.color.xm_accent);
        int accentDark = ContextCompat.getColor(holder.itemView.getContext(), R.color.xm_accent_dark);
        int surfaceAlt = ContextCompat.getColor(holder.itemView.getContext(), R.color.xm_surface_alt);
        int textPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.xm_text_primary);
        holder.selectButton.setStrokeWidth(selected ? dp(holder.itemView, 1) : 0);
        holder.selectButton.setStrokeColor(ColorStateList.valueOf(selected ? accent : ContextCompat.getColor(holder.itemView.getContext(), R.color.xm_stroke_soft)));
        holder.selectButton.setBackgroundTintList(ColorStateList.valueOf(selected ? surfaceAlt : accent));
        holder.selectButton.setTextColor(selected ? textPrimary : accentDark);
        holder.deleteButton.setVisibility(item.custom ? View.VISIBLE : View.GONE);
        holder.selectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(item);
            }
        });
        holder.testButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTest(item);
            }
        });
        holder.debugButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDebug(item);
            }
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class SourceViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView hostView;
        final TextView typeView;
        final MaterialButton selectButton;
        final MaterialButton testButton;
        final MaterialButton debugButton;
        final MaterialButton deleteButton;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.source_item_title);
            hostView = itemView.findViewById(R.id.source_item_host);
            typeView = itemView.findViewById(R.id.source_item_type);
            selectButton = itemView.findViewById(R.id.source_item_select_button);
            testButton = itemView.findViewById(R.id.source_item_test_button);
            debugButton = itemView.findViewById(R.id.source_item_debug_button);
            deleteButton = itemView.findViewById(R.id.source_item_delete_button);
        }
    }
}
