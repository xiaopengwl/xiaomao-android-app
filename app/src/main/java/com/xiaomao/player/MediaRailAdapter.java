package com.xiaomao.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MediaRailAdapter extends RecyclerView.Adapter<MediaRailAdapter.MediaRailViewHolder> {
    public interface OnItemClickListener {
        void onClick(NativeDrpyEngine.MediaItem item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(NativeDrpyEngine.MediaItem item, View anchor);
    }

    private final ArrayList<NativeDrpyEngine.MediaItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;
    private String sourceLabel = "";

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel.trim();
        notifyDataSetChanged();
    }

    public void submitList(List<NativeDrpyEngine.MediaItem> nextItems) {
        items.clear();
        if (nextItems != null) {
            items.addAll(nextItems);
        }
        notifyDataSetChanged();
    }

    public int getDataCount() {
        return items.size();
    }

    @NonNull
    @Override
    public MediaRailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_rail, parent, false);
        UiEffects.bindPressScale(view);
        return new MediaRailViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaRailViewHolder holder, int position) {
        NativeDrpyEngine.MediaItem item = items.get(position);
        holder.titleView.setText(item.title.isEmpty() ? "未命名影片" : item.title);
        holder.remarkView.setText(item.remark.isEmpty() ? "点击查看详情" : item.remark);
        holder.sourceView.setText(sourceLabel.isEmpty() ? "当前片源" : sourceLabel);
        holder.badgeView.setText(shortLabel(item.remark));
        PosterLoader.load(holder.posterView, item.poster, item.title);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(item, holder.itemView);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String shortLabel(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "热播";
        }
        if (text.length() > 8) {
            return text.substring(0, 8);
        }
        return text;
    }

    static final class MediaRailViewHolder extends RecyclerView.ViewHolder {
        final ImageView posterView;
        final TextView badgeView;
        final TextView titleView;
        final TextView remarkView;
        final TextView sourceView;

        MediaRailViewHolder(@NonNull View itemView) {
            super(itemView);
            posterView = itemView.findViewById(R.id.card_poster);
            badgeView = itemView.findViewById(R.id.card_badge);
            titleView = itemView.findViewById(R.id.card_title);
            remarkView = itemView.findViewById(R.id.card_remark);
            sourceView = itemView.findViewById(R.id.card_source);
        }
    }
}
