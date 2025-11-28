package com.example.notesmarterai.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.notesmarterai.R
import com.example.notesmarterai.adapter.NoteAdapter
import com.example.notesmarterai.model.LocalNoteManager
import com.example.notesmarterai.model.Note
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: NoteAdapter
    private lateinit var toolbar: MaterialToolbar

    // Bottom Action Bar Views
    private lateinit var bottomSelectionBar: MaterialCardView
    private lateinit var btnPinSelected: LinearLayout
    private lateinit var btnDeleteSelected: LinearLayout
    private lateinit var tvPinAction: TextView
    private lateinit var ivPinAction: ImageView

    private val allNotesList = mutableListOf<Note>()
    private val displayList = mutableListOf<Note>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isGuest = false

    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Views
        recyclerView = findViewById(R.id.recyclerViewNotes)
        fabAdd = findViewById(R.id.fabAddNote)
        toolbar = findViewById(R.id.toolbar)

        bottomSelectionBar = findViewById(R.id.bottomSelectionBar)
        btnPinSelected = findViewById(R.id.btnPinSelected)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        tvPinAction = findViewById(R.id.tvPinAction)
        ivPinAction = findViewById(R.id.ivPinAction)

        // Setup Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 2. Setup RecyclerView
        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        adapter = NoteAdapter(
            displayList,
            onNoteClick = { note -> handleNoteClick(note) },
            onNoteLongClick = { note -> handleNoteLongClick(note) }
        )
        recyclerView.adapter = adapter

        // 3. Listeners
        fabAdd.setOnClickListener { startActivity(Intent(this, AddNoteActivity::class.java)) }
        btnPinSelected.setOnClickListener { pinSelectedNotes() }
        btnDeleteSelected.setOnClickListener { showDeleteSelectedConfirmation() }

        // 4. Data Loading
        isGuest = intent.getBooleanExtra("IS_GUEST", false)
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("NightMode", false)
        if (isNight) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    // --- SELECTION LOGIC ---

    private fun handleNoteLongClick(note: Note) {
        if (!isSelectionMode) {
            isSelectionMode = true
            bottomSelectionBar.visibility = View.VISIBLE
            fabAdd.visibility = View.GONE
        }
        toggleSelection(note)
    }

    private fun handleNoteClick(note: Note) {
        if (isSelectionMode) {
            toggleSelection(note)
        } else {
            openEditScreen(note)
        }
    }

    private fun toggleSelection(note: Note) {
        note.isSelected = !note.isSelected
        adapter.notifyDataSetChanged()

        val selectedNotes = displayList.filter { it.isSelected }
        if (selectedNotes.isEmpty()) {
            exitSelectionMode()
        } else {
            updateBottomBarUI(selectedNotes)
        }
    }

    private fun updateBottomBarUI(selectedNotes: List<Note>) {
        // Logic: If ALL selected notes are pinned -> Show Unpin. Otherwise -> Show Pin.
        val allPinned = selectedNotes.all { it.isPinned }

        if (allPinned) {
            tvPinAction.text = "Unpin"
            // Optionally change icon to something else, or keep same pin icon
            ivPinAction.setImageResource(R.drawable.ic_pin_outline)
        } else {
            tvPinAction.text = "Pin"
            ivPinAction.setImageResource(R.drawable.ic_pin_outline)
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        bottomSelectionBar.visibility = View.GONE
        fabAdd.visibility = View.VISIBLE
        displayList.forEach { it.isSelected = false }
        adapter.notifyDataSetChanged()
    }

    // --- PIN ACTION ---
    private fun pinSelectedNotes() {
        val selectedNotes = displayList.filter { it.isSelected }
        if (selectedNotes.isEmpty()) return

        // Check logic again: If all pinned -> Unpin them. Else -> Pin them.
        val allPinned = selectedNotes.all { it.isPinned }
        val newStatus = !allPinned // Toggle status

        selectedNotes.forEach { note ->
            note.isPinned = newStatus
            saveNoteUpdate(note)
        }

        exitSelectionMode()
        val msg = if (newStatus) "Notes Pinned" else "Notes Unpinned"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshData()
    }

    // --- DELETE ACTION (Same as before) ---
    private fun showDeleteSelectedConfirmation() {
        val selectedCount = displayList.count { it.isSelected }
        AlertDialog.Builder(this)
            .setTitle("Delete Notes")
            .setMessage("Delete $selectedCount selected notes?")
            .setPositiveButton("Yes") { _, _ -> deleteSelectedNotes() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteSelectedNotes() {
        val selectedNotes = displayList.filter { it.isSelected }
        selectedNotes.forEach { note ->
            if (isGuest || auth.currentUser == null) {
                LocalNoteManager.deleteNote(this, note.id)
            } else {
                val userId = auth.currentUser!!.uid
                if (note.id.isNotEmpty()) {
                    db.collection("users").document(userId).collection("notes").document(note.id).delete()
                }
            }
        }
        exitSelectionMode()
        Toast.makeText(this, "Notes Deleted", Toast.LENGTH_SHORT).show()
        if(isGuest) loadGuestNotes()
    }

    private fun saveNoteUpdate(note: Note) {
        if (isGuest || auth.currentUser == null) {
            LocalNoteManager.saveNote(this, note)
        } else {
            val userId = auth.currentUser!!.uid
            db.collection("users").document(userId).collection("notes").document(note.id).set(note)
        }
    }

    // --- DATA LOADING & SORTING ---

    private fun refreshData() {
        val currentUser = auth.currentUser
        if (isGuest || currentUser == null) {
            loadGuestNotes()
        } else {
            loadFirebaseNotes(currentUser.uid)
        }
    }

    private fun loadGuestNotes() {
        allNotesList.clear()
        allNotesList.addAll(LocalNoteManager.getNotes(this))
        sortAndDisplay()
    }

    private fun loadFirebaseNotes(userId: String) {
        db.collection("users").document(userId).collection("notes")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                if (snapshots != null) {
                    allNotesList.clear()
                    for (document in snapshots) {
                        val note = document.toObject(Note::class.java)
                        note.id = document.id
                        allNotesList.add(note)
                    }
                    sortAndDisplay()
                }
            }
    }

    private fun sortAndDisplay() {
        // Sort: Pinned first, then by Timestamp descending
        allNotesList.sortWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })
        updateDisplayList(allNotesList)
    }

    private fun updateDisplayList(list: List<Note>) {
        displayList.clear()
        displayList.addAll(list)
        adapter.notifyDataSetChanged()
    }

    // --- MENU (Search, Filter, Night Mode) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val iconColor = ContextCompat.getColor(this, R.color.toolbar_icon_tint)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView

        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        searchIcon?.setColorFilter(iconColor)

        val closeIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(iconColor)

        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setTextColor(iconColor)
        searchEditText?.setHintTextColor(iconColor)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        val nightItem = menu.findItem(R.id.action_night_mode)
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        nightItem.isChecked = prefs.getBoolean("NightMode", false)
        for (i in 0 until menu!!.size()) {
            menu.getItem(i).icon?.setTint(iconColor)
        }
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

    private fun filterList(keyword: String?) {
        if (keyword.isNullOrEmpty()) {
            sortAndDisplay()
        } else {
            val filtered = allNotesList.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                        it.finalNote.contains(keyword, ignoreCase = true) ||
                        it.category.contains(keyword, ignoreCase = true)
            }
            displayList.clear()
            displayList.addAll(filtered)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showCategoryFilterDialog() {
        val categories = allNotesList.map { it.category }.distinct().filter { it.isNotEmpty() }.toMutableList()
        categories.add(0, "All")
        val options = categories.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setItems(options) { _, which ->
                val selected = options[which]
                if (selected == "All") sortAndDisplay()
                else {
                    val filtered = allNotesList.filter { it.category == selected }
                    displayList.clear()
                    displayList.addAll(filtered)
                    adapter.notifyDataSetChanged()
                }
            }
            .show()
    }

    private fun toggleNightMode(item: MenuItem) {
        val isNowNight = !item.isChecked
        item.isChecked = isNowNight
        getSharedPreferences("Settings", Context.MODE_PRIVATE).edit().putBoolean("NightMode", isNowNight).apply()
        if (isNowNight) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
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

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}