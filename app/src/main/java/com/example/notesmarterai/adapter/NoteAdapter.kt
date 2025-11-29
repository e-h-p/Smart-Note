package com.example.notesmarterai.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.model.Note
import com.google.android.material.card.MaterialCardView

class NoteAdapter(
    private val noteList: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        val tvSummary: TextView = itemView.findViewById(R.id.tvSummary)
        val ivPinIndicator: ImageView = itemView.findViewById(R.id.ivPinIndicator)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = noteList[position]
        val context = holder.itemView.context

        holder.tvTitle.text = note.title
        holder.tvCategory.text = note.category
        holder.tvPriority.text = "P: ${note.priority}"
        holder.tvSummary.text = if (note.aiSummary.isNotEmpty()) note.aiSummary else note.finalNote

        // --- NEW: DYNAMIC PRIORITY COLOR ---
        val priorityColor = when (note.priority) {
            in 1..3 -> ContextCompat.getColor(context, R.color.priority_low)  // Green
            in 4..6 -> ContextCompat.getColor(context, R.color.priority_med)  // Yellow
            else -> ContextCompat.getColor(context, R.color.priority_high)    // Red
        }
        holder.tvPriority.setTextColor(priorityColor)
        // -----------------------------------

        // Pin Indicator
        if (note.isPinned) {
            holder.ivPinIndicator.visibility = View.VISIBLE
        } else {
            holder.ivPinIndicator.visibility = View.GONE
        }

        // Selection Visuals
        if (note.isSelected) {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_selected))
            holder.cardView.strokeWidth = 4
            holder.cardView.strokeColor = Color.parseColor("#FF6200EE")
        } else {
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_default))
            holder.cardView.strokeWidth = 0
        }

        holder.itemView.setOnClickListener { onNoteClick(note) }
        holder.itemView.setOnLongClickListener { onNoteLongClick(note); true }
    }

    override fun getItemCount(): Int = noteList.size
}