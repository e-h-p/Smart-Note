package com.example.notesmarterai.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesmarterai.R
import com.example.notesmarterai.adapter.NoteAdapter
import com.example.notesmarterai.model.LocalNoteManager
import com.example.notesmarterai.model.Note
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: NoteAdapter
    private lateinit var toolbar: MaterialToolbar

    // We keep two lists: One for all data, one for the current filtered view
    private val allNotesList = mutableListOf<Note>()
    private val displayList = mutableListOf<Note>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var isGuest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Views
        recyclerView = findViewById(R.id.recyclerViewNotes)
        fabAdd = findViewById(R.id.fabAddNote)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

        // 2. Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NoteAdapter(
            displayList,
            onNoteClick = { note -> openEditScreen(note) },
            onDeleteClick = { note -> showDeleteConfirmation(note) }
        )
        recyclerView.adapter = adapter

        // 3. Setup Add Button
        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddNoteActivity::class.java))
        }

        // 4. Determine User Type & Load Data
        isGuest = intent.getBooleanExtra("IS_GUEST", false)
        val currentUser = auth.currentUser

        // Load Night Mode Preference
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("NightMode", false)
        if (isNight) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        val currentUser = auth.currentUser
        if (isGuest || currentUser == null) {
            loadGuestNotes()
        } else {
            loadFirebaseNotes(currentUser.uid)
        }
    }

    // --- DATA LOADING ---

    private fun loadGuestNotes() {
        allNotesList.clear()
        allNotesList.addAll(LocalNoteManager.getNotes(this))
        updateDisplayList(allNotesList)
    }

    private fun loadFirebaseNotes(userId: String) {
        db.collection("users").document(userId).collection("notes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots != null) {
                    allNotesList.clear()
                    for (document in snapshots) {
                        val note = document.toObject(Note::class.java)
                        note.id = document.id
                        allNotesList.add(note)
                    }
                    updateDisplayList(allNotesList)
                }
            }
    }

    private fun updateDisplayList(list: List<Note>) {
        displayList.clear()
        displayList.addAll(list)
        adapter.notifyDataSetChanged()
    }

    // --- MENU & FEATURES ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        // SEARCH FUNCTIONALITY
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // CHECK NIGHT MODE STATE
        val nightItem = menu.findItem(R.id.action_night_mode)
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        nightItem.isChecked = prefs.getBoolean("NightMode", false)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_filter -> showCategoryFilterDialog()
            R.id.action_night_mode -> toggleNightMode(item)
            R.id.action_logout -> showLogoutConfirmation()
        }
        return super.onOptionsItemSelected(item)
    }

    // --- FEATURE 1: FILTER BY KEYWORD ---
    private fun filterList(keyword: String?) {
        if (keyword.isNullOrEmpty()) {
            updateDisplayList(allNotesList)
        } else {
            val filtered = allNotesList.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                        it.finalNote.contains(keyword, ignoreCase = true) ||
                        it.category.contains(keyword, ignoreCase = true)
            }
            updateDisplayList(filtered)
        }
    }

    // --- FEATURE 2: FILTER BY CATEGORY ---
    private fun showCategoryFilterDialog() {
        val categories = allNotesList.map { it.category }.distinct().filter { it.isNotEmpty() }.toMutableList()
        categories.add(0, "All")
        val options = categories.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(options) { _, which ->
                val selected = options[which]
                if (selected == "All") {
                    updateDisplayList(allNotesList)
                } else {
                    val filtered = allNotesList.filter { it.category == selected }
                    updateDisplayList(filtered)
                }
            }
            .show()
    }

    // --- FEATURE 3: NIGHT MODE ---
    private fun toggleNightMode(item: MenuItem) {
        val isNowNight = !item.isChecked
        item.isChecked = isNowNight

        // Save preference
        getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .edit().putBoolean("NightMode", isNowNight).apply()

        // Apply Mode
        if (isNowNight) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    // --- GENERIC ACTIONS ---

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Return to Login screen?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditScreen(note: Note) {
        val intent = Intent(this, AddNoteActivity::class.java)
        // Pass all note fields
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
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Delete '${note.title}'?")
            .setPositiveButton("Yes") { _, _ -> deleteNote(note) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteNote(note: Note) {
        if (isGuest || auth.currentUser == null) {
            LocalNoteManager.deleteNote(this, note.id)
            loadGuestNotes() // Refresh list
            Toast.makeText(this, "Deleted locally", Toast.LENGTH_SHORT).show()
        } else {
            val userId = auth.currentUser!!.uid
            if (note.id.isNotEmpty()) {
                db.collection("users").document(userId).collection("notes").document(note.id)
                    .delete()
                    .addOnSuccessListener { Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}