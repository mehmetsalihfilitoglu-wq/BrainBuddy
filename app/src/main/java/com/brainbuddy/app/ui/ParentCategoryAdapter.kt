package com.brainbuddy.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.databinding.ItemParentCategoryBinding

data class ParentCategoryItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val iconRes: Int,
    val onClick: () -> Unit
)

class ParentCategoryAdapter(
    private val items: List<ParentCategoryItem>
) : RecyclerView.Adapter<ParentCategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemParentCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val b: ItemParentCategoryBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: ParentCategoryItem) {
            b.title.setText(item.titleRes)
            b.subtitle.setText(item.subtitleRes)
            b.icon.setImageResource(item.iconRes)
            b.cardRoot.setOnClickListener { item.onClick() }
        }
    }
}
