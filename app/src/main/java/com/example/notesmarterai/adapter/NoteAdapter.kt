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
        val ivReminderActive: ImageView = itemView.findViewById(R.id.ivReminderActive) // NEW
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

        // Priority Colors
        val priorityColor = when (note.priority) {
            in 1..3 -> ContextCompat.getColor(context, R.color.priority_low)
            in 4..6 -> ContextCompat.getColor(context, R.color.priority_med)
            else -> ContextCompat.getColor(context, R.color.priority_high)
        }
        holder.tvPriority.setTextColor(priorityColor)
        // Tint the reminder icon same as priority for a clean look
        holder.ivReminderActive.setColorFilter(priorityColor)

        // --- REMINDER ICON LOGIC ---
        if (note.reminderTime.equals("None", ignoreCase = true) || note.reminderTime.isEmpty()) {
            holder.ivReminderActive.visibility = View.GONE
        } else {
            // Parse Date to check if expired
            val currentTime = System.currentTimeMillis()
            val reminderTime = parseDateToMillis(note.reminderTime)

            if (reminderTime > currentTime) {
                holder.ivReminderActive.visibility = View.VISIBLE // Active
            } else {
                holder.ivReminderActive.visibility = View.GONE // Expired
            }
        }

        // Pin Indicator
        holder.ivPinIndicator.visibility = if (note.isPinned) View.VISIBLE else View.GONE

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

    // Helper to parse the 24h format saved in DB
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