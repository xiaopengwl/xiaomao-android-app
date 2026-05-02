package com.xiaomao.player;

import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CategorySidebarAdapter extends RecyclerView.Adapter<CategorySidebarAdapter.SidebarViewHolder> {
    public interface OnItemClickListener {
        void onClick(NativeDrpyEngine.Category category);
    }

    private final ArrayList<NativeDrpyEngine.Category> items = new ArrayList<>();
    private OnItemClickListener listener;
    private String selectedUrl = "";

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<NativeDrpyEngine.Category> nextItems, String selectedUrl) {
        items.clear();
        if (nextItems != null) {
            items.addAll(nextItems);
        }
        this.selectedUrl = selectedUrl == null ? "" : selectedUrl.trim();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SidebarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_sidebar, parent, false);
        UiEffects.bindPressScale(view);
        return new SidebarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SidebarViewHolder holder, int position) {
        NativeDrpyEngine.Category category = items.get(position);
        boolean selected = selectedUrl.equals(category.url);
        holder.labelView.setText(category.name.isEmpty() ? "分类" : category.name);
        holder.labelView.setTextColor(ContextCompat.getColor(holder.labelView.getContext(),
                selected ? R.color.xm_accent : R.color.xm_text_primary));
        holder.labelView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        holder.itemView.setBackground(buildBackground(holder.itemView, selected));
        holder.itemView.setElevation(selected ? dp(holder.itemView, 1) : 0f);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(category);
            }
        });
    }

    @Override
        public int getItemCount() {
        return items.size();
    }

    private GradientDrawable buildBackground(View view, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(12f * view.getResources().getDisplayMetrics().density);
        drawable.setColor(ContextCompat.getColor(view.getContext(), selected ? R.color.xm_info_bg : android.R.color.transparent));
        drawable.setStroke((int) (view.getResources().getDisplayMetrics().density + 0.5f),
                ContextCompat.getColor(view.getContext(), selected ? R.color.xm_accent : android.R.color.transparent));
        return drawable;
    }

    private float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    static final class SidebarViewHolder extends RecyclerView.ViewHolder {
        final TextView labelView;

        SidebarViewHolder(@NonNull View itemView) {
            super(itemView);
            labelView = itemView.findViewById(R.id.sidebar_label);
        }
    }
}
