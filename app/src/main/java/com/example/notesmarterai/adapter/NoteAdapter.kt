package com.example.notesmarterai.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.model.Note
import java.text.SimpleDateFormat
import java.util.*

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
        val ivReminderActive: ImageView = itemView.findViewById(R.id.ivReminderActive)
        // CHANGED: This is now a generic View or LinearLayout, not MaterialCardView
        val cardContainer: View = itemView.findViewById(R.id.cardView)
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
        holder.tvPriority.text = "●" // Using a dot for the new design
        holder.tvSummary.text = if (note.aiSummary.isNotEmpty()) note.aiSummary else note.finalNote

        // Priority Colors
        val priorityColor = when (note.priority) {
            in 1..3 -> ContextCompat.getColor(context, R.color.priority_low)
            in 4..6 -> ContextCompat.getColor(context, R.color.priority_med)
            else -> ContextCompat.getColor(context, R.color.priority_high)
        }
        holder.tvPriority.setTextColor(priorityColor)
        holder.ivReminderActive.setColorFilter(ContextCompat.getColor(context, R.color.glass_text_secondary))

        // --- REMINDER ICON LOGIC ---
        if (note.reminderTime.equals("None", ignoreCase = true) || note.reminderTime.isEmpty()) {
            holder.ivReminderActive.visibility = View.GONE
        } else {
            val currentTime = System.currentTimeMillis()
            val reminderTime = parseDateToMillis(note.reminderTime)
            if (reminderTime > currentTime) {
                holder.ivReminderActive.visibility = View.VISIBLE
            } else {
                holder.ivReminderActive.visibility = View.GONE
            }
        }

        // Pin Indicator
        holder.ivPinIndicator.visibility = if (note.isPinned) View.VISIBLE else View.GONE

        // Selection Visuals (Glass Effect)
        if (note.isSelected) {
            // Apply a subtle overlay for selection
            holder.cardContainer.setBackgroundResource(R.drawable.bg_glass_card)
            holder.cardContainer.background.setTint(ContextCompat.getColor(context, R.color.card_selection_overlay))
        } else {
            // Restore default glass
            holder.cardContainer.setBackgroundResource(R.drawable.bg_glass_card)
            holder.cardContainer.background.setTintList(null)
        }

        holder.itemView.setOnClickListener { onNoteClick(note) }
        holder.itemView.setOnLongClickListener { onNoteLongClick(note); true }
    }

    override fun getItemCount(): Int = noteList.size

    private fun parseDateToMillis(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = format.parse(dateString)
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}