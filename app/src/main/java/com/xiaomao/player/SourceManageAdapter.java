package com.xiaomao.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SourceManageAdapter extends RecyclerView.Adapter<SourceManageAdapter.SourceViewHolder> {
    public interface Listener {
        void onSelect(SourceStore.SourceItem item);
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
        holder.hostView.setText(item.host.isEmpty() ? "未提供站点地址" : item.host);
        holder.typeView.setText(item.custom ? "自定义" : "内置");
        holder.selectButton.setText(selected ? "当前使用中" : "切换到这里");
        holder.selectButton.setEnabled(!selected);
        holder.deleteButton.setVisibility(item.custom ? View.VISIBLE : View.GONE);
        holder.selectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(item);
            }
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });
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
        final MaterialButton deleteButton;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.source_item_title);
            hostView = itemView.findViewById(R.id.source_item_host);
            typeView = itemView.findViewById(R.id.source_item_type);
            selectButton = itemView.findViewById(R.id.source_item_select_button);
            deleteButton = itemView.findViewById(R.id.source_item_delete_button);
        }
    }
}
