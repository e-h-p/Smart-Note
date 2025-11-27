package com.example.notesmarterai.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.adapter.NoteAdapter
import com.example.notesmarterai.model.Note
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: NoteAdapter
    private val noteList = mutableListOf<Note>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewNotes)
        fabAdd = findViewById(R.id.fabAddNote)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Adapter with TWO actions:
        // 1. Click on note -> Edit
        // 2. Click on trash -> Delete
        adapter = NoteAdapter(
            noteList,
            onNoteClick = { note ->
                openEditScreen(note)
            },
            onDeleteClick = { note ->
                showDeleteConfirmation(note)
            }
        )

        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            // Click (+) -> Create New (No data passed)
            startActivity(Intent(this, AddNoteActivity::class.java))
        }

        loadNotesFromFirebase()
    }

    // <--- NEW FUNCTION: Prepare data for Edit Mode
    private fun openEditScreen(note: Note) {
        val intent = Intent(this, AddNoteActivity::class.java)
        intent.putExtra("NOTE_ID", note.id)
        intent.putExtra("NOTE_TITLE", note.title)
        intent.putExtra("NOTE_CATEGORY", note.category)
        intent.putExtra("NOTE_PRIORITY", note.priority)
        intent.putExtra("NOTE_REMINDER", note.reminderTime)
        intent.putExtra("NOTE_FINAL", note.finalNote) // This is the AI Rewritten part
        intent.putExtra("NOTE_SUMMARY", note.aiSummary)

        // NEW: Pass the rough note so it appears in the top box
        intent.putExtra("NOTE_ROUGH", note.roughNote)

        intent.putExtra("NOTE_TIMESTAMP", note.timestamp)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(note: Note) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete Note")
        builder.setMessage("Are you sure you want to delete '${note.title}'?")
        builder.setIcon(android.R.drawable.ic_dialog_alert)
        builder.setPositiveButton("Yes") { dialog, _ ->
            deleteNoteFromFirebase(note)
            dialog.dismiss()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun deleteNoteFromFirebase(note: Note) {
        if (note.id.isNotEmpty()) {
            db.collection("notes").document(note.id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadNotesFromFirebase() {
        // Sorting by timestamp descending (newest first)
        db.collection("notes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    noteList.clear()
                    for (document in snapshots) {
                        val note = document.toObject(Note::class.java)
                        note.id = document.id
                        noteList.add(note)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }
}