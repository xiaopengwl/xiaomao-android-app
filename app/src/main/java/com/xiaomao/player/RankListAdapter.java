package com.xiaomao.player;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankListAdapter extends RecyclerView.Adapter<RankListAdapter.RankViewHolder> {
    public interface OnItemClickListener {
        void onClick(NativeDrpyEngine.MediaItem item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(NativeDrpyEngine.MediaItem item, View anchor);
    }

    private final ArrayList<NativeDrpyEngine.MediaItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemLongClickListener longClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
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

    public ArrayList<NativeDrpyEngine.MediaItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rank_card, parent, false);
        UiEffects.bindPressScale(view);
        return new RankViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        NativeDrpyEngine.MediaItem item = items.get(position);
        holder.numberView.setText(String.valueOf(position + 1));
        holder.titleView.setText(item.title.isEmpty() ? "未命名影片" : item.title);
        holder.remarkView.setText(item.remark.isEmpty() ? "当前片源未返回简介，点击进入详情页查看选集。" : item.remark);
        holder.heatView.setText(String.format(Locale.CHINA, "%d", Math.max(5231, 8450 - (position * 217))));
        holder.numberView.setBackground(buildRankBadge(holder.numberView, position));
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

    static final class RankViewHolder extends RecyclerView.ViewHolder {
        final TextView numberView;
        final ImageView posterView;
        final TextView titleView;
        final TextView remarkView;
        final TextView heatView;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);
            numberView = itemView.findViewById(R.id.rank_number);
            posterView = itemView.findViewById(R.id.rank_poster);
            titleView = itemView.findViewById(R.id.rank_title);
            remarkView = itemView.findViewById(R.id.rank_remark);
            heatView = itemView.findViewById(R.id.rank_heat);
        }
    }

    private GradientDrawable buildRankBadge(TextView view, int position) {
        int colorRes;
        if (position == 0) {
            colorRes = R.color.xm_rank_1;
        } else if (position == 1) {
            colorRes = R.color.xm_rank_2;
        } else if (position == 2) {
            colorRes = R.color.xm_rank_3;
        } else {
            colorRes = R.color.xm_rank_other;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ContextCompat.getColor(view.getContext(), colorRes));
        drawable.setCornerRadii(new float[]{
                dp(view, 12), dp(view, 12),
                dp(view, 0), dp(view, 0),
                dp(view, 12), dp(view, 12),
                dp(view, 0), dp(view, 0)
        });
        return drawable;
    }

    private float dp(TextView view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
