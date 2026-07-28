package com.edumio.app.quiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edumio.app.databinding.ItemResultRowBinding

data class ResultRow(
    val question: Question,
    val selectedIndex: Int,
    val correctIndex: Int
) {
    val isCorrect: Boolean get() = selectedIndex == correctIndex
}

class ResultsAdapter(private val items: List<ResultRow>) :
    RecyclerView.Adapter<ResultsAdapter.VH>() {

    class VH(val b: ItemResultRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemResultRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val q = row.question

        holder.b.title.text = "${position + 1}) ${q.subject.tr} • ${q.gradeTag}"
        holder.b.stem.text = q.stem

        // sadece doğru/yanlış gösteriyoruz (doğru cevabı yazmıyoruz)
        holder.b.status.text = if (row.isCorrect) "✅ Doğru" else "❌ Yanlış"
    }
}