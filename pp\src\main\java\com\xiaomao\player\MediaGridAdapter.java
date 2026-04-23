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

public class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.MediaViewHolder> {
    public interface OnItemClickListener {
        void onClick(NativeDrpyEngine.MediaItem item);
    }

    private final ArrayList<NativeDrpyEngine.MediaItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
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

    public List<NativeDrpyEngine.MediaItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_card, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        NativeDrpyEngine.MediaItem item = items.get(position);
        holder.titleView.setText(item.title.isEmpty() ? "未命名影片" : item.title);
        holder.remarkView.setText(item.remark.isEmpty() ? "点击查看详情与选集" : item.remark);
        holder.badgeView.setText(position < 6 ? "热播" : "片库");
        PosterLoader.load(holder.posterView, item.poster, item.title);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class MediaViewHolder extends RecyclerView.ViewHolder {
        final ImageView posterView;
        final TextView badgeView;
        final TextView titleView;
        final TextView remarkView;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            posterView = itemView.findViewById(R.id.card_poster);
            badgeView = itemView.findViewById(R.id.card_badge);
            titleView = itemView.findViewById(R.id.card_title);
            remarkView = itemView.findViewById(R.id.card_remark);
        }
    }
}
