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
    private lateinit var etCategory: EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        initViews()
        setupSpinners()
        setupListeners()

        // Setup Toolbar Back Button
        toolbar.setNavigationOnClickListener { finish() }

        if (intent.hasExtra("NOTE_ID")) {
            isEditMode = true
            existingNoteId = intent.getStringExtra("NOTE_ID")
            existingTimestamp = intent.getLongExtra("NOTE_TIMESTAMP", System.currentTimeMillis())

            tvHeader.text = "Edit Note"
            btnSave.text = "Update"

            etTitle.setText(intent.getStringExtra("NOTE_TITLE"))
            etCategory.setText(intent.getStringExtra("NOTE_CATEGORY"))

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
        etCategory = findViewById(R.id.etCategory)
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

    private fun setupSpinners() {
        val amPmAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("AM", "PM"))
        amPmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAmPm.adapter = amPmAdapter

        val priorities = (1..9).toList()
        val pAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = pAdapter
    }

    private fun setupListeners() {
        cbNoReminder.setOnCheckedChangeListener { _, isChecked ->
            val enable = !isChecked
            etDay.isEnabled = enable; etMonth.isEnabled = enable; etYear.isEnabled = enable
            etHour.isEnabled = enable; etMinute.isEnabled = enable; spinnerAmPm.isEnabled = enable

            if (isChecked) {
                // Clear fields if no reminder
                etDay.text.clear(); etMonth.text.clear(); etYear.text.clear()
                etHour.text.clear(); etMinute.text.clear()
            } else {
                // Populate with current date if enabling
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
        // CRITICAL FIX: Ensure check is case-insensitive and handles empty
        if (timeString.equals("None", ignoreCase = true) || timeString.isEmpty()) {
            cbNoReminder.isChecked = true
        } else {
            cbNoReminder.isChecked = false
            try {
                if (timeString.contains("/")) {
                    val parts = timeString.split(" ")
                    val datePart = parts.find { it.contains("/") } ?: ""
                    val dateSplit = datePart.split("/")
                    if (dateSplit.size == 3) { etDay.setText(dateSplit[0]); etMonth.setText(dateSplit[1]); etYear.setText(dateSplit[2]) }
                }
                if (timeString.contains(":")) {
                    val parts = timeString.split(" ")
                    val timePart = parts.find { it.contains(":") } ?: ""
                    val timeSplit = timePart.split(":")
                    if (timeSplit.size >= 2) { etHour.setText(timeSplit[0]); etMinute.setText(timeSplit[1]) }
                    if (timeString.contains("PM", ignoreCase = true)) spinnerAmPm.setSelection(1) else spinnerAmPm.setSelection(0)
                }
            } catch (e: Exception) { cbNoReminder.isChecked = true }
        }
    }

    private fun saveNoteDispatcher() {
        val selectedPriority = spinnerPriority.selectedItem as Int
        val note = Note(
            title = etTitle.text.toString(),
            category = etCategory.text.toString(),
            reminderTime = getCombinedTime(),
            priority = selectedPriority,
            finalNote = etFinalNote.text.toString(),
            aiSummary = etAiSummary.text.toString(),
            roughNote = etNoteInput.text.toString(),
            timestamp = if (isEditMode) existingTimestamp else System.currentTimeMillis(),
            id = existingNoteId ?: ""
        )

        val currentUser = auth.currentUser
        if (currentUser != null) saveNoteToFirebaseSmart(currentUser.uid, note)
        else {
            LocalNoteManager.saveNote(this, note)
            Toast.makeText(this, "Saved to Device (Guest)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveNoteToFirebaseSmart(userId: String, note: Note) {
        val userNotesRef = db.collection("users").document(userId).collection("notes")
        val operation = if (isEditMode && note.id.isNotEmpty()) userNotesRef.document(note.id).set(note) else userNotesRef.add(note)

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

    // --- AI GENERATION LOGIC (UPDATED) ---

    private fun generateNoteWithGemini(userText: String) {
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val currentDate = sdf.format(Date())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"

        // UPDATED PROMPT: STRICTER RULES FOR "None"
        val promptText = """
            Analyze text: "$userText". Current Date: $currentDate.
            Output JSON. Rules:
            1. Title: Generate a short title.
            2. Category: Infer category or "General".
            3. ReminderTime: Extract date/time ONLY if explicitly mentioned in text (e.g. "tomorrow", "at 5pm", "on Friday"). If no specific time is mentioned, output "None". DO NOT default to today's date.
            4. Priority: Infer 1-9 or 5.
            
            Schema: {"Title":"str", "Category":"str", "ReminderTime":"dd/MM/yyyy HH:MM AM or None", "Priority":int, "FinalNote":"str", "AiSummary":"str"}
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
                        if (candidates.isNullOrEmpty()) {
                            // Fallback if API response is empty
                            applyDefaults(userText)
                            return@runOnUiThread
                        }
                        val content = (candidates[0] as Map<*, *>)["content"] as Map<*, *>
                        val parts = (content["parts"] as List<*>)[0] as Map<*, *>
                        var aiText = parts["text"] as String
                        aiText = aiText.replace("```json", "").replace("```", "").trim()

                        val generatedNote = gson.fromJson(aiText, Note::class.java)

                        // Apply fields safely (Use defaults if null)
                        etTitle.setText(if (!generatedNote.title.isNullOrEmpty()) generatedNote.title else userText.take(30))
                        etCategory.setText(if (!generatedNote.category.isNullOrEmpty()) generatedNote.category else "General")

                        val aiPriority = generatedNote.priority
                        if (aiPriority in 1..9) spinnerPriority.setSelection(aiPriority - 1) else spinnerPriority.setSelection(4) // 5

                        etAiSummary.setText(generatedNote.aiSummary ?: "")
                        etFinalNote.setText(generatedNote.finalNote ?: userText)

                        // Fix for Reminder logic: Handle case insensitivity and null
                        val remTime = generatedNote.reminderTime
                        if (remTime == null || remTime.equals("None", ignoreCase = true)) {
                            setSplitTime("None")
                        } else {
                            setSplitTime(remTime)
                        }

                    } catch (e: Exception) {
                        // --- FALLBACK LOGIC ---
                        // If JSON parsing fails, simply use default values instead of showing an error
                        applyDefaults(userText)
                        Toast.makeText(this@AddNoteActivity, "Auto-filled defaults (AI didn't detect specific details)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Helper to apply defaults when AI fails or is incomplete
    private fun applyDefaults(text: String) {
        etTitle.setText(text.take(30))
        etCategory.setText("General")
        spinnerPriority.setSelection(4) // Priority 5
        etFinalNote.setText(text)
        etAiSummary.setText(text)
        setSplitTime("None")
    }

    private fun showErrorPopup(message: String) {
        AlertDialog.Builder(this).setTitle("Error").setMessage(message).setPositiveButton("OK") { d, _ -> d.dismiss() }.show()
    }
}