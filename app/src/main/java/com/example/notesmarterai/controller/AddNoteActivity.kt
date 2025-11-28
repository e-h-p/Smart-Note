package com.example.notesmarterai.controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.notesmarterai.R
import com.example.notesmarterai.model.LocalNoteManager
import com.example.notesmarterai.model.Note
import com.example.notesmarterai.model.Secrets
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

    // UI Variables
    private lateinit var etNoteInput: EditText
    private lateinit var etTitle: EditText
    private lateinit var etCategory: EditText
    private lateinit var etPriority: EditText
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

    // Backend Variables
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // API Key from Secrets.kt
    private val apiKey = Secrets.GEMINI_API_KEY

    // State Variables
    private var isEditMode = false
    private var existingNoteId: String? = null
    private var existingTimestamp: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        initViews()
        setupSpinner()
        setupListeners()

        // Check if we are Editing an existing note
        if (intent.hasExtra("NOTE_ID")) {
            isEditMode = true
            existingNoteId = intent.getStringExtra("NOTE_ID")
            existingTimestamp = intent.getLongExtra("NOTE_TIMESTAMP", System.currentTimeMillis())

            // Update button text to indicate an edit
            btnSave.text = "Update"

            // Pre-fill data
            etTitle.setText(intent.getStringExtra("NOTE_TITLE"))
            etCategory.setText(intent.getStringExtra("NOTE_CATEGORY"))
            etPriority.setText(intent.getIntExtra("NOTE_PRIORITY", 1).toString())
            etFinalNote.setText(intent.getStringExtra("NOTE_FINAL"))
            etAiSummary.setText(intent.getStringExtra("NOTE_SUMMARY"))
            etNoteInput.setText(intent.getStringExtra("NOTE_ROUGH") ?: "")

            val savedTime = intent.getStringExtra("NOTE_REMINDER") ?: "None"
            setSplitTime(savedTime)
        } else {
            setSplitTime("None")
        }

        // AI Generation Button
        btnGenerate.setOnClickListener {
            val rawText = etNoteInput.text.toString()
            if (rawText.isNotEmpty()) {
                generateNoteWithGemini(rawText)
            } else {
                Toast.makeText(this, "Please enter some text first", Toast.LENGTH_SHORT).show()
            }
        }

        // Save Button
        btnSave.setOnClickListener {
            saveNoteDispatcher()
        }
    }

    private fun initViews() {
        etNoteInput = findViewById(R.id.etNoteInput)
        etTitle = findViewById(R.id.etTitle)
        etCategory = findViewById(R.id.etCategory)
        etPriority = findViewById(R.id.etPriority)
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

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("AM", "PM"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAmPm.adapter = adapter
    }

    private fun setupListeners() {
        cbNoReminder.setOnCheckedChangeListener { _, isChecked ->
            val enable = !isChecked
            etDay.isEnabled = enable
            etMonth.isEnabled = enable
            etYear.isEnabled = enable
            etHour.isEnabled = enable
            etMinute.isEnabled = enable
            spinnerAmPm.isEnabled = enable

            if (isChecked) {
                etDay.text.clear(); etMonth.text.clear(); etYear.text.clear()
                etHour.text.clear(); etMinute.text.clear()
            } else {
                if (etDay.text.isEmpty()) {
                    val calendar = Calendar.getInstance()
                    etDay.setText(calendar.get(Calendar.DAY_OF_MONTH).toString())
                    etMonth.setText((calendar.get(Calendar.MONTH) + 1).toString())
                    etYear.setText(calendar.get(Calendar.YEAR).toString())
                }
            }
        }
    }

    private fun getCombinedTime(): String {
        if (cbNoReminder.isChecked) return "None"

        val dd = etDay.text.toString().padStart(2, '0').ifEmpty { "01" }
        val mm = etMonth.text.toString().padStart(2, '0').ifEmpty { "01" }
        val yyyy = etYear.text.toString().ifEmpty { Calendar.getInstance().get(Calendar.YEAR).toString() }

        val hh = etHour.text.toString().padStart(2, '0').ifEmpty { "00" }
        val mmTime = etMinute.text.toString().padStart(2, '0').ifEmpty { "00" }
        val amPm = spinnerAmPm.selectedItem.toString()

        return "$dd/$mm/$yyyy $hh:$mmTime $amPm"
    }

    private fun setSplitTime(timeString: String) {
        if (timeString.equals("None", ignoreCase = true) || timeString.isEmpty()) {
            cbNoReminder.isChecked = true
        } else {
            cbNoReminder.isChecked = false
            try {
                val calendar = Calendar.getInstance()
                // Defaults
                val todayDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
                val todayMonth = (calendar.get(Calendar.MONTH) + 1).toString()
                val todayYear = calendar.get(Calendar.YEAR).toString()

                if (timeString.contains("/")) {
                    val parts = timeString.split(" ")
                    val datePart = parts.find { it.contains("/") } ?: ""
                    val dateSplit = datePart.split("/")
                    if (dateSplit.size == 3) {
                        etDay.setText(dateSplit[0])
                        etMonth.setText(dateSplit[1])
                        etYear.setText(dateSplit[2])
                    }
                } else {
                    etDay.setText(todayDay); etMonth.setText(todayMonth); etYear.setText(todayYear)
                }

                if (timeString.contains(":")) {
                    val parts = timeString.split(" ")
                    val timePart = parts.find { it.contains(":") } ?: ""
                    val timeSplit = timePart.split(":")
                    if (timeSplit.size >= 2) {
                        etHour.setText(timeSplit[0])
                        etMinute.setText(timeSplit[1])
                    }
                    if (timeString.contains("PM", ignoreCase = true)) spinnerAmPm.setSelection(1)
                    else spinnerAmPm.setSelection(0)
                }
            } catch (e: Exception) {
                cbNoReminder.isChecked = true
            }
        }
    }

    // --- MAIN SAVE LOGIC ---
    private fun saveNoteDispatcher() {
        val note = Note(
            title = etTitle.text.toString(),
            category = etCategory.text.toString(),
            reminderTime = getCombinedTime(),
            priority = etPriority.text.toString().toIntOrNull() ?: 1,
            finalNote = etFinalNote.text.toString(),
            aiSummary = etAiSummary.text.toString(),
            roughNote = etNoteInput.text.toString(),
            timestamp = if (isEditMode) existingTimestamp else System.currentTimeMillis(),
            id = existingNoteId ?: ""
        )

        val currentUser = auth.currentUser

        if (currentUser != null) {
            // USER IS LOGGED IN -> Smart Firebase Save
            saveNoteToFirebaseSmart(currentUser.uid, note)
        } else {
            // GUEST USER -> Local Storage Only
            LocalNoteManager.saveNote(this, note)
            Toast.makeText(this, "Saved to Device (Guest)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveNoteToFirebaseSmart(userId: String, note: Note) {
        val userNotesRef = db.collection("users").document(userId).collection("notes")

        // 1. Initiate the Write Operation
        // This writes to local cache IMMEDIATELY (Offline support)
        val operation = if (isEditMode && note.id.isNotEmpty()) {
            userNotesRef.document(note.id).set(note)
        } else {
            userNotesRef.add(note)
        }

        // 2. Check Connection to notify user correctly
        if (isNetworkAvailable()) {
            Toast.makeText(this, "Syncing to Cloud...", Toast.LENGTH_SHORT).show()

            // This listener only fires when the server confirms the write
            operation.addOnSuccessListener {
                Toast.makeText(this, "Uploaded Successfully!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Saved to Device (Will upload when Online)", Toast.LENGTH_LONG).show()
        }

        // 3. Close the screen immediately.
        // We do not wait for the success listener because the local save is already done.
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- AI LOGIC ---
    private fun showErrorPopup(message: String) {
        AlertDialog.Builder(this).setTitle("Error").setMessage(message).setPositiveButton("OK") { d, _ -> d.dismiss() }.show()
    }

    private fun generateNoteWithGemini(userText: String) {
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"
        val promptText = """
            Analyze the following text and extract/generate a JSON object.
            Text: "$userText"
            Current Date: $currentDate
            
            Schema:
            {
                "Title": "string",
                "Category": "string",
                "ReminderTime": "string (Format: 'dd/MM/yyyy HH:MM AM' or 'None')",
                "Priority": integer (1-10),
                "FinalNote": "string",
                "AiSummary": "string"
            }
            Return ONLY the JSON. No markdown.
        """.trimIndent()

        val jsonRequest = """{ "contents": [{ "parts": [{ "text": ${Gson().toJson(promptText)} }] }] }"""
        val body = jsonRequest.toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder().url(url).post(body).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    showErrorPopup("Network Error: Check internet connection")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true

                    if (!response.isSuccessful) {
                        showErrorPopup("AI Error: ${response.message}")
                        return@runOnUiThread
                    }

                    try {
                        val gson = Gson()
                        val map = gson.fromJson(resBody, Map::class.java)
                        val candidates = map["candidates"] as? List<*>
                        if (candidates.isNullOrEmpty()) return@runOnUiThread

                        val content = (candidates[0] as Map<*, *>)["content"] as Map<*, *>
                        val parts = (content["parts"] as List<*>)[0] as Map<*, *>
                        var aiText = parts["text"] as String

                        // Cleanup Markdown code blocks if Gemini sends them
                        aiText = aiText.replace("```json", "").replace("```", "").trim()

                        val generatedNote = gson.fromJson(aiText, Note::class.java)

                        // Populate UI
                        etTitle.setText(generatedNote.title)
                        etCategory.setText(generatedNote.category)
                        etPriority.setText(generatedNote.priority.toString())
                        etAiSummary.setText(generatedNote.aiSummary)
                        etFinalNote.setText(generatedNote.finalNote)
                        setSplitTime(generatedNote.reminderTime)

                    } catch (e: Exception) {
                        // Fallback if AI JSON parsing fails
                        etFinalNote.setText(userText)
                        Toast.makeText(this@AddNoteActivity, "AI Formatting Failed - Text copied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}