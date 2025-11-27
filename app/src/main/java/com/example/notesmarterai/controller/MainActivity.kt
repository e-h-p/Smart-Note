package com.example.notesmarterai.controller

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.adapter.NoteAdapter
import com.example.notesmarterai.model.Note
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: NoteAdapter
    private val noteList = mutableListOf<Note>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance() // NEW: Auth Instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SECURITY CHECK: If no user, kick back to Login
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewNotes)
        fabAdd = findViewById(R.id.fabAddNote)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NoteAdapter(
            noteList,
            onNoteClick = { note -> openEditScreen(note) },
            onDeleteClick = { note -> showDeleteConfirmation(note) }
        )

        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }

        loadNotesFromFirebase(currentUser.uid)
    }

    private fun loadNotesFromFirebase(userId: String) {
        // CHANGED PATH: users -> userID -> notes
        // This ensures users only see THEIR notes
        db.collection("users").document(userId).collection("notes")
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

    // --- Logout Feature (Optional but recommended) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Logout")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
    // ------------------------------------------------

    private fun openEditScreen(note: Note) {
        val intent = Intent(this, AddNoteActivity::class.java)
        intent.putExtra("NOTE_ID", note.id)
        intent.putExtra("NOTE_TITLE", note.title)
        intent.putExtra("NOTE_CATEGORY", note.category)
        intent.putExtra("NOTE_PRIORITY", note.priority)
        intent.putExtra("NOTE_REMINDER", note.reminderTime)
        intent.putExtra("NOTE_FINAL", note.finalNote)
        intent.putExtra("NOTE_SUMMARY", note.aiSummary)
        intent.putExtra("NOTE_ROUGH", note.roughNote)
        intent.putExtra("NOTE_TIMESTAMP", note.timestamp)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(note: Note) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete Note")
        builder.setMessage("Are you sure you want to delete '${note.title}'?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            deleteNoteFromFirebase(note)
            dialog.dismiss()
        }
        builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun deleteNoteFromFirebase(note: Note) {
        val userId = auth.currentUser?.uid ?: return
        if (note.id.isNotEmpty()) {
            db.collection("users").document(userId).collection("notes").document(note.id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                }
        }
    }
}