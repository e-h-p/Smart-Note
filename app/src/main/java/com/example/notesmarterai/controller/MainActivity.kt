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

    // State
    private var isSelectionMode = false
    private var currentSortMode = "DATE_DESC"

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

    // --- SELECTION & CLICK LOGIC ---

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
            openViewScreen(note)
        }
    }

    private fun openViewScreen(note: Note) {
        val intent = Intent(this, ViewNoteActivity::class.java)
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

    private fun toggleSelection(note: Note) {
        note.isSelected = !note.isSelected
        adapter.notifyDataSetChanged()
        val selectedNotes = displayList.filter { it.isSelected }
        if (selectedNotes.isEmpty()) exitSelectionMode() else updateBottomBarUI(selectedNotes)
    }

    private fun updateBottomBarUI(selectedNotes: List<Note>) {
        val allPinned = selectedNotes.all { it.isPinned }
        if (allPinned) {
            tvPinAction.text = "Unpin"
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

    // --- PIN & DELETE ACTIONS ---

    private fun pinSelectedNotes() {
        val selectedNotes = displayList.filter { it.isSelected }
        if (selectedNotes.isEmpty()) return
        val allPinned = selectedNotes.all { it.isPinned }
        val newStatus = !allPinned
        selectedNotes.forEach { note -> note.isPinned = newStatus; saveNoteUpdate(note) }
        exitSelectionMode()
        refreshData()
    }

    private fun showDeleteSelectedConfirmation() {
        val selectedCount = displayList.count { it.isSelected }
        AlertDialog.Builder(this).setTitle("Delete").setMessage("Delete $selectedCount notes?")
            .setPositiveButton("Yes") { _, _ -> deleteSelectedNotes() }.setNegativeButton("No", null).show()
    }

    private fun deleteSelectedNotes() {
        val selectedNotes = displayList.filter { it.isSelected }
        selectedNotes.forEach { note ->
            if (isGuest) LocalNoteManager.deleteNote(this, note.id)
            else db.collection("users").document(auth.currentUser!!.uid).collection("notes").document(note.id).delete()
        }
        exitSelectionMode()
        if (isGuest) loadGuestNotes()
    }

    private fun saveNoteUpdate(note: Note) {
        if (isGuest) LocalNoteManager.saveNote(this, note)
        else db.collection("users").document(auth.currentUser!!.uid).collection("notes").document(note.id).set(note)
    }

    // --- DATA & SORTING ---

    private fun refreshData() {
        if (isGuest) loadGuestNotes() else loadFirebaseNotes(auth.currentUser!!.uid)
    }

    private fun loadGuestNotes() {
        allNotesList.clear()
        allNotesList.addAll(LocalNoteManager.getNotes(this))
        sortAndDisplay()
    }

    private fun loadFirebaseNotes(userId: String) {
        db.collection("users").document(userId).collection("notes").addSnapshotListener { snapshots, _ ->
            if (snapshots != null) {
                allNotesList.clear()
                for (doc in snapshots) {
                    val note = doc.toObject(Note::class.java)
                    note.id = doc.id
                    allNotesList.add(note)
                }
                sortAndDisplay()
            }
        }
    }

    private fun sortAndDisplay() {
        val sortedList = allNotesList.sortedWith(compareByDescending<Note> { it.isPinned }
            .thenComparator { n1, n2 ->
                when (currentSortMode) {
                    "PRIORITY_HIGH_LOW" -> n2.priority.compareTo(n1.priority)
                    "PRIORITY_LOW_HIGH" -> n1.priority.compareTo(n2.priority)
                    else -> n2.timestamp.compareTo(n1.timestamp)
                }
            })
        updateDisplayList(sortedList)
    }

    private fun updateDisplayList(list: List<Note>) {
        displayList.clear()
        displayList.addAll(list)
        adapter.notifyDataSetChanged()
    }

    // --- MENU ACTIONS ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val iconColor = ContextCompat.getColor(this, R.color.toolbar_icon_tint)
        for (i in 0 until menu!!.size()) { menu.getItem(i).icon?.setTint(iconColor) }

        val searchItem = menu.findItem(R.id.action_search)
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
                if(newText.isNullOrEmpty()) sortAndDisplay()
                else {
                    val filtered = allNotesList.filter {
                        it.title.contains(newText, true) || it.finalNote.contains(newText, true)
                    }
                    updateDisplayList(filtered)
                }
                return true
            }
        })
        return true
    }

    // FIX: Sync menu state here to ensure checkbox is correct when menu opens
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val nightItem = menu?.findItem(R.id.action_night_mode)
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("NightMode", false)
        nightItem?.isChecked = isNight
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort -> showSortDialog()
            R.id.action_filter -> showCategoryFilterDialog()
            R.id.action_night_mode -> toggleNightMode(item)
            R.id.action_logout -> showLogoutConfirmation()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val options = arrayOf("Date (Newest First)", "Priority (High → Low)", "Priority (Low → High)")
        AlertDialog.Builder(this)
            .setTitle("Sort By")
            .setItems(options) { _, which ->
                currentSortMode = when (which) {
                    1 -> "PRIORITY_HIGH_LOW"
                    2 -> "PRIORITY_LOW_HIGH"
                    else -> "DATE_DESC"
                }
                sortAndDisplay()
            }
            .show()
    }

    private fun showCategoryFilterDialog() {
        val categories = allNotesList.map { it.category }.distinct().filter { it.isNotEmpty() }.toMutableList()
        categories.add(0, "All")
        AlertDialog.Builder(this).setTitle("Select Category").setItems(categories.toTypedArray()) { _, which ->
            val selected = categories[which]
            if (selected == "All") sortAndDisplay()
            else {
                val filtered = allNotesList.filter { it.category == selected }
                val sortedFiltered = filtered.sortedWith(compareByDescending<Note> { it.isPinned }
                    .thenComparator { n1, n2 ->
                        when (currentSortMode) {
                            "PRIORITY_HIGH_LOW" -> n2.priority.compareTo(n1.priority)
                            "PRIORITY_LOW_HIGH" -> n1.priority.compareTo(n2.priority)
                            else -> n2.timestamp.compareTo(n1.timestamp)
                        }
                    })
                updateDisplayList(sortedFiltered)
            }
        }.show()
    }

    private fun toggleNightMode(item: MenuItem) {
        // Logic fix: Toggle based on current pref, not just UI state
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentMode = prefs.getBoolean("NightMode", false)
        val newMode = !currentMode

        prefs.edit().putBoolean("NightMode", newMode).apply()
        item.isChecked = newMode // Visual update

        AppCompatDelegate.setDefaultNightMode(if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Sign out?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                finish()
            }.setNegativeButton("No", null).show()
    }

    override fun onBackPressed() {
        if (isSelectionMode) exitSelectionMode() else super.onBackPressed()
    }
}