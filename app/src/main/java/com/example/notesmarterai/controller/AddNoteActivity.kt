package com.example.notesmarterai.controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.notesmarterai.R
import com.example.notesmarterai.model.LocalNoteManager
import com.example.notesmarterai.model.Note
import com.example.notesmarterai.model.NotificationScheduler
import com.example.notesmarterai.model.Secrets
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AddNoteActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvHeader: TextView
    private lateinit var etNoteInput: EditText
    private lateinit var etTitle: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var layoutCategoryInput: LinearLayout
    private lateinit var etCategoryNew: EditText
    private lateinit var btnRevertCategory: ImageButton
    private lateinit var spinnerPriority: Spinner
    private lateinit var etFinalNote: EditText
    private lateinit var etAiSummary: EditText
    private lateinit var etDay: EditText
    private lateinit var etMonth: EditText
    private lateinit var etYear: EditText
    private lateinit var etHour: EditText
    private lateinit var etMinute: EditText
    private lateinit var spinnerAmPm: Spinner
    private lateinit var cbNoReminder: CheckBox
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGenerate: Button
    private lateinit var btnSave: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
    private val apiKey = Secrets.GEMINI_API_KEY

    private var isEditMode = false
    private var existingNoteId: String? = null
    private var existingTimestamp: Long = System.currentTimeMillis()

    private val existingCategories = mutableListOf<String>()
    private val ADD_NEW_CATEGORY = "Add New..."
    private val defaultCategories = listOf("General", "Work", "Personal", "Ideas", "To-Do", "Shopping", "Finance", "Health", "Education")

    private var is24HourSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        is24HourSystem = DateFormat.is24HourFormat(this)

        initViews()
        setupSpinners()
        setupListeners()
        fetchCategoriesAndPopulate()

        toolbar.setNavigationOnClickListener { finish() }

        if (intent.hasExtra("NOTE_ID")) {
            isEditMode = true
            existingNoteId = intent.getStringExtra("NOTE_ID")
            existingTimestamp = intent.getLongExtra("NOTE_TIMESTAMP", System.currentTimeMillis())

            tvHeader.text = "Edit Note"
            btnSave.text = "Update"

            etTitle.setText(intent.getStringExtra("NOTE_TITLE"))
            val savedCategory = intent.getStringExtra("NOTE_CATEGORY") ?: "General"
            etCategoryNew.setText(savedCategory)

            val p = intent.getIntExtra("NOTE_PRIORITY", 1)
            if (p in 1..9) spinnerPriority.setSelection(p - 1)

            etFinalNote.setText(intent.getStringExtra("NOTE_FINAL"))
            etAiSummary.setText(intent.getStringExtra("NOTE_SUMMARY"))
            etNoteInput.setText(intent.getStringExtra("NOTE_ROUGH") ?: "")
            setSplitTime(intent.getStringExtra("NOTE_REMINDER") ?: "None")
        } else {
            tvHeader.text = "Create Note"
            setSplitTime("None")
        }

        btnGenerate.setOnClickListener {
            val rawText = etNoteInput.text.toString()
            if (rawText.isNotEmpty()) generateNoteWithGemini(rawText)
            else Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener { saveNoteDispatcher() }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvHeader = findViewById(R.id.tvHeader)
        etNoteInput = findViewById(R.id.etNoteInput)
        etTitle = findViewById(R.id.etTitle)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        layoutCategoryInput = findViewById(R.id.layoutCategoryInput)
        etCategoryNew = findViewById(R.id.etCategoryNew)
        btnRevertCategory = findViewById(R.id.btnRevertCategory)
        spinnerPriority = findViewById(R.id.spinnerPriority)
        etFinalNote = findViewById(R.id.etFinalNote)
        etAiSummary = findViewById(R.id.etAiSummary)
        progressBar = findViewById(R.id.progressBarAI)
        btnGenerate = findViewById(R.id.btnGenerateAI)
        btnSave = findViewById(R.id.btnSaveNote)
        etDay = findViewById(R.id.etDay)
        etMonth = findViewById(R.id.etMonth)
        etYear = findViewById(R.id.etYear)
        etHour = findViewById(R.id.etHour)
        etMinute = findViewById(R.id.etMinute)
        spinnerAmPm = findViewById(R.id.spinnerAmPm)
        cbNoReminder = findViewById(R.id.cbNoReminder)
    }

    private fun fetchCategoriesAndPopulate() {
        existingCategories.clear()
        existingCategories.addAll(defaultCategories)
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).collection("notes").get()
                .addOnSuccessListener { snapshots ->
                    snapshots.forEach { doc ->
                        val cat = doc.getString("Category")
                        if (!cat.isNullOrEmpty() && !existingCategories.contains(cat)) {
                            existingCategories.add(cat)
                        }
                    }
                    setupCategorySpinner()
                }
                .addOnFailureListener { setupCategorySpinner() }
        } else {
            val localNotes = LocalNoteManager.getNotes(this)
            localNotes.forEach {
                if (it.category.isNotEmpty() && !existingCategories.contains(it.category)) {
                    existingCategories.add(it.category)
                }
            }
            setupCategorySpinner()
        }
    }

    private fun setupCategorySpinner() {
        val uniqueCategories = existingCategories.distinct().sorted().toMutableList()
        uniqueCategories.add(ADD_NEW_CATEGORY)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, uniqueCategories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
        if (isEditMode) {
            val currentCat = etCategoryNew.text.toString()
            if (uniqueCategories.contains(currentCat)) {
                spinnerCategory.setSelection(uniqueCategories.indexOf(currentCat))
                showCategorySpinner(true)
            } else {
                showCategorySpinner(false)
            }
        }
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = uniqueCategories[position]
                if (selected == ADD_NEW_CATEGORY) {
                    etCategoryNew.setText("")
                    showCategorySpinner(false)
                    etCategoryNew.requestFocus()
                } else {
                    etCategoryNew.setText(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        btnRevertCategory.setOnClickListener {
            showCategorySpinner(true)
            spinnerCategory.setSelection(0)
        }
    }

    private fun showCategorySpinner(show: Boolean) {
        if (show) {
            spinnerCategory.visibility = View.VISIBLE
            layoutCategoryInput.visibility = View.GONE
        } else {
            spinnerCategory.visibility = View.GONE
            layoutCategoryInput.visibility = View.VISIBLE
        }
    }

    private fun setupSpinners() {
        val priorities = (1..9).toList()
        val pAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = pAdapter

        val amPmAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("AM", "PM"))
        amPmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAmPm.adapter = amPmAdapter

        if (is24HourSystem) {
            spinnerAmPm.visibility = View.GONE
            etHour.hint = "00-23"
        } else {
            spinnerAmPm.visibility = View.VISIBLE
            etHour.hint = "01-12"
        }
    }

    private fun setupListeners() {
        cbNoReminder.setOnCheckedChangeListener { _, isChecked ->
            val enable = !isChecked
            etDay.isEnabled = enable; etMonth.isEnabled = enable; etYear.isEnabled = enable
            etHour.isEnabled = enable; etMinute.isEnabled = enable
            spinnerAmPm.isEnabled = enable && !is24HourSystem

            if (isChecked) {
                etDay.text.clear(); etMonth.text.clear(); etYear.text.clear()
                etHour.text.clear(); etMinute.text.clear()
            } else {
                if (etDay.text.isEmpty()) {
                    val calendar = Calendar.getInstance()
                    etDay.setText(calendar.get(Calendar.DAY_OF_MONTH).toString())
                    etMonth.setText((calendar.get(Calendar.MONTH) + 1).toString())
                    etYear.setText(calendar.get(Calendar.YEAR).toString())

                    // FIX: Pre-fill time fields with current time
                    var hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val minute = calendar.get(Calendar.MINUTE)

                    if (!is24HourSystem) {
                        // 12H Format Logic
                        val isPm = hour >= 12
                        if (hour > 12) hour -= 12
                        if (hour == 0) hour = 12
                        etHour.setText(hour.toString().padStart(2, '0'))
                        spinnerAmPm.setSelection(if (isPm) 1 else 0) // Set Spinner correctly
                    } else {
                        // 24H Format Logic
                        etHour.setText(hour.toString().padStart(2, '0'))
                    }
                    etMinute.setText(minute.toString().padStart(2, '0'))
                }
            }
        }
    }

    private fun getCombinedTime(): String {
        if (cbNoReminder.isChecked) return "None"

        val dd = etDay.text.toString().padStart(2, '0').ifEmpty { "01" }
        val mm = etMonth.text.toString().padStart(2, '0').ifEmpty { "01" }
        val yyyy = etYear.text.toString().ifEmpty { Calendar.getInstance().get(Calendar.YEAR).toString() }

        var hhStr = etHour.text.toString().padStart(2, '0').ifEmpty { "00" }
        val mmTime = etMinute.text.toString().padStart(2, '0').ifEmpty { "00" }

        // Normalize to 24H format internally for standardized saving
        if (!is24HourSystem) {
            var hh = hhStr.toIntOrNull() ?: 0
            val amPm = spinnerAmPm.selectedItem.toString()

            if (amPm == "PM" && hh < 12) hh += 12
            if (amPm == "AM" && hh == 12) hh = 0

            hhStr = hh.toString().padStart(2, '0')
        }

        return "$dd/$mm/$yyyy $hhStr:$mmTime"
    }

    private fun setSplitTime(timeString: String) {
        if (timeString.equals("None", ignoreCase = true) || timeString.isEmpty()) {
            cbNoReminder.isChecked = true
        } else {
            cbNoReminder.isChecked = false
            try {
                if (timeString.contains("/")) {
                    val parts = timeString.split(" ")
                    val datePart = parts[0]
                    val dateSplit = datePart.split("/")
                    if (dateSplit.size == 3) {
                        etDay.setText(dateSplit[0])
                        etMonth.setText(dateSplit[1])
                        etYear.setText(dateSplit[2])
                    }

                    if (parts.size > 1) {
                        val timePart = parts[1]
                        val timeSplit = timePart.split(":")
                        if (timeSplit.size >= 2) {
                            var hour = timeSplit[0].toInt()
                            val minute = timeSplit[1]

                            if (is24HourSystem) {
                                etHour.setText(hour.toString().padStart(2, '0'))
                                etMinute.setText(minute)
                            } else {
                                var isPm = false
                                if (hour >= 12) {
                                    isPm = true
                                    if (hour > 12) hour -= 12
                                }
                                if (hour == 0) hour = 12

                                etHour.setText(hour.toString().padStart(2, '0'))
                                etMinute.setText(minute)
                                spinnerAmPm.setSelection(if (isPm) 1 else 0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                cbNoReminder.isChecked = true
            }
        }
    }

    private fun saveNoteDispatcher() {
        val finalCategory = etCategoryNew.text.toString().ifEmpty { "General" }
        val selectedPriority = spinnerPriority.selectedItem as Int

        val note = Note(
            title = etTitle.text.toString(),
            category = finalCategory,
            reminderTime = getCombinedTime(),
            priority = selectedPriority,
            finalNote = etFinalNote.text.toString(),
            aiSummary = etAiSummary.text.toString(),
            roughNote = etNoteInput.text.toString(),
            timestamp = if (isEditMode) existingTimestamp else System.currentTimeMillis(),
            id = existingNoteId ?: "local_${System.currentTimeMillis()}"
        )

        NotificationScheduler.scheduleReminder(this, note)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            saveNoteToFirebaseSmart(currentUser.uid, note)
        } else {
            LocalNoteManager.saveNote(this, note)
            Toast.makeText(this, "Saved to Device (Guest)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveNoteToFirebaseSmart(userId: String, note: Note) {
        val userNotesRef = db.collection("users").document(userId).collection("notes")
        val operation = userNotesRef.document(note.id).set(note)

        if (isNetworkAvailable()) {
            Toast.makeText(this, "Syncing to Cloud...", Toast.LENGTH_SHORT).show()
            operation.addOnSuccessListener { Toast.makeText(this, "Uploaded Successfully!", Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(this, "Saved to Device (Will upload when Online)", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun generateNoteWithGemini(userText: String) {
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"
        val existingCatsString = existingCategories.distinct().joinToString(", ")

        // UPDATED PROMPT: Request 24H format explicitly for standardized saving
        val promptText = """
            Analyze text: "$userText". Current Date: $currentDate.
            Existing Categories: [$existingCatsString].
            Output JSON. Rules:
            1. Category: Use an existing category if it fits perfectly. If NOT, create a concise new one.
            2. ReminderTime: Extract date/time ONLY if explicitly mentioned. Format must be 'dd/MM/yyyy HH:mm' (24-hour). If no time mentioned, output "None".
            3. Priority: 1-9.
            4. AiSummary: Ultra-short, concise summary (max 15 words).
            
            Schema: {"Title":"str", "Category":"str", "ReminderTime":"str", "Priority":int, "FinalNote":"str", "AiSummary":"str"}
            Return ONLY JSON.
        """.trimIndent()

        val jsonRequest = """{ "contents": [{ "parts": [{ "text": ${Gson().toJson(promptText)} }] }] }"""
        val body = jsonRequest.toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder().url(url).post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE; btnGenerate.isEnabled = true;
                    showErrorPopup("Network Error")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                    try {
                        val gson = Gson()
                        val map = gson.fromJson(resBody, Map::class.java)
                        val candidates = map["candidates"] as? List<*>
                        if (candidates.isNullOrEmpty()) { applyDefaults(userText); return@runOnUiThread }

                        val content = (candidates[0] as Map<*, *>)["content"] as Map<*, *>
                        val parts = (content["parts"] as List<*>)[0] as Map<*, *>
                        var aiText = parts["text"] as String
                        aiText = aiText.replace("```json", "").replace("```", "").trim()

                        val generatedNote = gson.fromJson(aiText, Note::class.java)

                        etTitle.setText(if (!generatedNote.title.isNullOrEmpty()) generatedNote.title else userText.take(30))

                        val aiCat = generatedNote.category ?: "General"
                        etCategoryNew.setText(aiCat)
                        val adapter = spinnerCategory.adapter as ArrayAdapter<String>
                        val pos = adapter.getPosition(aiCat)
                        if (pos >= 0) {
                            spinnerCategory.setSelection(pos)
                            showCategorySpinner(true)
                        } else {
                            showCategorySpinner(false)
                        }
                        val aiPriority = generatedNote.priority
                        if (aiPriority in 1..9) spinnerPriority.setSelection(aiPriority - 1) else spinnerPriority.setSelection(4)

                        etAiSummary.setText(generatedNote.aiSummary ?: "")
                        etFinalNote.setText(generatedNote.finalNote ?: userText)

                        val remTime = generatedNote.reminderTime
                        if (remTime == null || remTime.equals("None", ignoreCase = true)) setSplitTime("None") else setSplitTime(remTime)

                    } catch (e: Exception) {
                        applyDefaults(userText)
                        Toast.makeText(this@AddNoteActivity, "Auto-filled defaults (AI issue)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun applyDefaults(text: String) {
        etTitle.setText(text.take(30))
        etCategoryNew.setText("General")
        showCategorySpinner(true)
        spinnerPriority.setSelection(4)
        etFinalNote.setText(text)
        etAiSummary.setText(text)
        setSplitTime("None")
    }

    private fun showErrorPopup(message: String) {
        AlertDialog.Builder(this).setTitle("Error").setMessage(message).setPositiveButton("OK") { d, _ -> d.dismiss() }.show()
    }
}