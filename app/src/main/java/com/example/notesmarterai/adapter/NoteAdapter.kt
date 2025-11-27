package com.example.notesmarterai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val noteList: List<Note>,
    private val onNoteClick: (Note) -> Unit,    // <--- NEW: Action for editing
    private val onDeleteClick: (Note) -> Unit   // Action for deleting
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        val tvSummary: TextView = itemView.findViewById(R.id.tvSummary)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = noteList[position]
        holder.tvTitle.text = note.title

        // Format timestamp for display
        val dateString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(note.timestamp))
        holder.tvCategory.text = "${note.category} • $dateString"

        holder.tvPriority.text = "P: ${note.priority}"
        holder.tvSummary.text = note.aiSummary

        // 1. Delete Button Click
        holder.btnDelete.setOnClickListener {
            onDeleteClick(note)
        }

        // 2. Whole Card Click (Triggers Edit)
        holder.itemView.setOnClickListener {
            onNoteClick(note)
        }
    }

    override fun getItemCount(): Int = noteList.size
}