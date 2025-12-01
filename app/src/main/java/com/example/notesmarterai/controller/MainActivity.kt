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
import androidx.activity.OnBackPressedCallback
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
import com.example.notesmarterai.model.NotificationScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
// import com.google.android.material.floatingactionbutton.FloatingActionButton // Removed
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    // UPDATED: Changed from FloatingActionButton to ImageView
    private lateinit var fabAdd: ImageView
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

    // Auth State
    private var isGuest = false
    private val GOOGLE_SIGN_IN_REQUEST_CODE = 9002

    // State Variables for Filters & Sort
    private var isSelectionMode = false
    private var currentSortMode = "DATE_DESC"
    private var currentCategory = "All"
    private var currentSearchQuery = ""

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

        // 4. Check Notification Channel (for reminders)
        NotificationScheduler.createNotificationChannel(this)

        // 5. Determine User Type & Load Data
        isGuest = intent.getBooleanExtra("IS_GUEST", auth.currentUser == null)

        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("NightMode", false)
        if (isNight) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 6. Back Press Logic
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Re-check guest state on resume in case they logged in elsewhere (edge case)
        isGuest = (auth.currentUser == null)
        invalidateOptionsMenu() // Refresh menu (Login vs Logout)
        refreshData()
    }

    // --- MIGRATION LOGIC ---

    private fun initiateGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val signInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(signInClient.signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    isGuest = false
                    Toast.makeText(this, "Signed in! Syncing notes...", Toast.LENGTH_SHORT).show()
                    migrateGuestNotesToFirebase()
                    invalidateOptionsMenu()
                    refreshData()
                } else {
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun migrateGuestNotesToFirebase() {
        val userId = auth.currentUser?.uid ?: return
        val localNotes = LocalNoteManager.getNotes(this)

        if (localNotes.isEmpty()) return

        val batch = db.batch()
        val userNotesRef = db.collection("users").document(userId).collection("notes")

        localNotes.forEach { note ->
            val docRef = userNotesRef.document()
            val newNote = note.copy(id = docRef.id)
            batch.set(docRef, newNote)
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Notes migrated to Cloud!", Toast.LENGTH_SHORT).show()
            LocalNoteManager.clearAll(this)
            refreshData()
        }.addOnFailureListener {
            Toast.makeText(this, "Migration failed. Notes kept locally.", Toast.LENGTH_LONG).show()
        }
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
            NotificationScheduler.cancelReminder(this, note)
        }
        exitSelectionMode()
        if (isGuest) loadGuestNotes()
    }

    private fun saveNoteUpdate(note: Note) {
        if (isGuest) LocalNoteManager.saveNote(this, note)
        else db.collection("users").document(auth.currentUser!!.uid).collection("notes").document(note.id).set(note)
    }

    // --- DATA LOADING ---

    private fun refreshData() {
        if (isGuest) loadGuestNotes() else loadFirebaseNotes(auth.currentUser!!.uid)
    }

    private fun loadGuestNotes() {
        allNotesList.clear()
        allNotesList.addAll(LocalNoteManager.getNotes(this))
        applyFiltersAndSort()
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
                applyFiltersAndSort()
            }
        }
    }

    private fun applyFiltersAndSort() {
        var resultList = allNotesList.toList()
        if (currentCategory != "All") {
            resultList = resultList.filter { it.category == currentCategory }
        }
        if (currentSearchQuery.isNotEmpty()) {
            resultList = resultList.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) ||
                        it.finalNote.contains(currentSearchQuery, ignoreCase = true) ||
                        it.category.contains(currentSearchQuery, ignoreCase = true)
            }
        }
        val sortedList = resultList.sortedWith(compareByDescending<Note> { it.isPinned }
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
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView

        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        searchIcon?.setColorFilter(iconColor)
        val closeIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon?.setColorFilter(iconColor)
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.setTextColor(iconColor)
        searchEditText?.setHintTextColor(iconColor)

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                currentSearchQuery = ""
                applyFiltersAndSort()
                invalidateOptionsMenu()
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText ?: ""
                applyFiltersAndSort()
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val nightItem = menu?.findItem(R.id.action_night_mode)
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("NightMode", false)
        nightItem?.isChecked = isNight

        val iconColor = ContextCompat.getColor(this, R.color.toolbar_icon_tint)
        for (i in 0 until menu!!.size()) {
            menu.getItem(i).icon?.setTint(iconColor)
        }

        val loginItem = menu?.findItem(R.id.action_login)
        val logoutItem = menu?.findItem(R.id.action_logout)

        if (isGuest) {
            loginItem?.isVisible = true
            logoutItem?.isVisible = false
        } else {
            loginItem?.isVisible = false
            logoutItem?.isVisible = true
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort -> showSortDialog()
            R.id.action_filter -> showCategoryFilterDialog()
            R.id.action_night_mode -> toggleNightMode(item)
            R.id.action_login -> initiateGoogleSignIn()
            R.id.action_logout -> showLogoutConfirmation()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val options = arrayOf("Date (Newest First)", "Priority (High → Low)", "Priority (Low → High)")
        AlertDialog.Builder(this).setTitle("Sort By").setItems(options) { _, which ->
            currentSortMode = when (which) {
                1 -> "PRIORITY_HIGH_LOW"
                2 -> "PRIORITY_LOW_HIGH"
                else -> "DATE_DESC"
            }
            applyFiltersAndSort()
        }.show()
    }

    private fun showCategoryFilterDialog() {
        val categories = allNotesList.map { it.category }.distinct().filter { it.isNotEmpty() }.sorted().toMutableList()
        categories.add(0, "All")
        AlertDialog.Builder(this).setTitle("Select Category").setItems(categories.toTypedArray()) { _, which ->
            currentCategory = categories[which]
            applyFiltersAndSort()
        }.show()
    }

    private fun toggleNightMode(item: MenuItem) {
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val currentMode = prefs.getBoolean("NightMode", false)
        val newMode = !currentMode
        prefs.edit().putBoolean("NightMode", newMode).apply()
        item.isChecked = newMode
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
}