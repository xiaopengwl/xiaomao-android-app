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

public class RankListAdapter extends RecyclerView.Adapter<RankListAdapter.RankViewHolder> {
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

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rank_card, parent, false);
        return new RankViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        NativeDrpyEngine.MediaItem item = items.get(position);
        holder.numberView.setText(String.valueOf(position + 1));
        holder.titleView.setText(item.title.isEmpty() ? "未命名影片" : item.title);
        holder.remarkView.setText(item.remark.isEmpty() ? "当前片源未返回简介，点击进入详情页查看选集。" : item.remark);
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

    static final class RankViewHolder extends RecyclerView.ViewHolder {
        final TextView numberView;
        final ImageView posterView;
        final TextView titleView;
        final TextView remarkView;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);
            numberView = itemView.findViewById(R.id.rank_number);
            posterView = itemView.findViewById(R.id.rank_poster);
            titleView = itemView.findViewById(R.id.rank_title);
            remarkView = itemView.findViewById(R.id.rank_remark);
        }
    }
}
