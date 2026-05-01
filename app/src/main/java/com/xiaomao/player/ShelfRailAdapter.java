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

public class ShelfRailAdapter extends RecyclerView.Adapter<ShelfRailAdapter.ShelfViewHolder> {
    public interface OnItemClickListener {
        void onClick(ShelfCard item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(ShelfCard item, View anchor);
    }

    public static final class ShelfCard {
        public final String title;
        public final String remark;
        public final String source;
        public final String poster;
        public final String badge;
        public final Object payload;

        public ShelfCard(String title, String remark, String source, String poster, String badge, Object payload) {
            this.title = safe(title);
            this.remark = safe(remark);
            this.source = safe(source);
            this.poster = safe(poster);
            this.badge = safe(badge);
            this.payload = payload;
        }
    }

    private final ArrayList<ShelfCard> items = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public void setOnItemClickListener(OnItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void submitList(List<ShelfCard> nextItems) {
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
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_rail, parent, false);
        UiEffects.bindPressScale(view);
        return new ShelfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        ShelfCard item = items.get(position);
        holder.titleView.setText(item.title.isEmpty() ? "\u672A\u547D\u540D" : item.title);
        holder.remarkView.setText(item.remark.isEmpty() ? "\u70B9\u51FB\u67E5\u770B" : item.remark);
        holder.sourceView.setText(item.source.isEmpty() ? "\u672C\u5730\u8BB0\u5F55" : item.source);
        holder.badgeView.setText(item.badge.isEmpty() ? "\u6536\u85CF" : item.badge);
        PosterLoader.load(holder.posterView, item.poster, item.title);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(item);
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

    static final class ShelfViewHolder extends RecyclerView.ViewHolder {
        final ImageView posterView;
        final TextView badgeView;
        final TextView titleView;
        final TextView remarkView;
        final TextView sourceView;

        ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            posterView = itemView.findViewById(R.id.card_poster);
            badgeView = itemView.findViewById(R.id.card_badge);
            titleView = itemView.findViewById(R.id.card_title);
            remarkView = itemView.findViewById(R.id.card_remark);
            sourceView = itemView.findViewById(R.id.card_source);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
