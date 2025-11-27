package com.example.notesmarterai.controller

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.notesmarterai.R
import com.example.notesmarterai.model.Note
import com.google.firebase.auth.FirebaseAuth // NEW
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AddNoteActivity : AppCompatActivity() {

    // ... (UI Variables same as before) ...
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

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance() // NEW

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey = "GEMINI API KEY"

    private var isEditMode = false
    private var existingNoteId: String? = null
    private var existingTimestamp: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        initViews()
        setupSpinner()
        setupListeners()

        if (intent.hasExtra("NOTE_ID")) {
            isEditMode = true
            existingNoteId = intent.getStringExtra("NOTE_ID")
            existingTimestamp = intent.getLongExtra("NOTE_TIMESTAMP", System.currentTimeMillis())

            btnSave.text = "Update Note"

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

        btnGenerate.setOnClickListener {
            val rawText = etNoteInput.text.toString()
            if (rawText.isNotEmpty()) {
                generateNoteWithGemini(rawText)
            } else {
                Toast.makeText(this, "Please enter some text first", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            saveNoteToFirebase()
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
        val amPmOptions = arrayOf("AM", "PM")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, amPmOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAmPm.adapter = adapter
    }

    private fun setupListeners() {
        cbNoReminder.setOnCheckedChangeListener { _, isChecked ->
            etDay.isEnabled = !isChecked
            etMonth.isEnabled = !isChecked
            etYear.isEnabled = !isChecked
            etHour.isEnabled = !isChecked
            etMinute.isEnabled = !isChecked
            spinnerAmPm.isEnabled = !isChecked
            if (isChecked) {
                etDay.text.clear(); etMonth.text.clear(); etYear.text.clear(); etHour.text.clear(); etMinute.text.clear()
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
        var dd = etDay.text.toString().padStart(2, '0')
        var mm = etMonth.text.toString().padStart(2, '0')
        var yyyy = etYear.text.toString()
        if (dd.isEmpty()) dd = "01"; if (mm.isEmpty()) mm = "01"; if (yyyy.isEmpty()) yyyy = Calendar.getInstance().get(Calendar.YEAR).toString()
        var hh = etHour.text.toString(); var mmTime = etMinute.text.toString()
        if (hh.isEmpty()) hh = "00"; if (mmTime.isEmpty()) mmTime = "00"
        hh = hh.padStart(2, '0'); mmTime = mmTime.padStart(2, '0')
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
                val todayDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
                val todayMonth = (calendar.get(Calendar.MONTH) + 1).toString()
                val todayYear = calendar.get(Calendar.YEAR).toString()

                if (timeString.contains("/")) {
                    val parts = timeString.split(" ")
                    val datePart = parts.find { it.contains("/") } ?: ""
                    val dateSplit = datePart.split("/")
                    if (dateSplit.size == 3) {
                        etDay.setText(dateSplit[0]); etMonth.setText(dateSplit[1]); etYear.setText(dateSplit[2])
                    }
                } else { etDay.setText(todayDay); etMonth.setText(todayMonth); etYear.setText(todayYear) }

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

    private fun saveNoteToFirebase() {
        // SECURITY CHECK
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val note = Note(
            title = etTitle.text.toString(),
            category = etCategory.text.toString(),
            reminderTime = getCombinedTime(),
            priority = etPriority.text.toString().toIntOrNull() ?: 1,
            finalNote = etFinalNote.text.toString(),
            aiSummary = etAiSummary.text.toString(),
            roughNote = etNoteInput.text.toString(),
            timestamp = System.currentTimeMillis()
        )

        // UPDATED PATH: users/{userId}/notes
        val userNotesRef = db.collection("users").document(userId).collection("notes")

        if (isEditMode && existingNoteId != null) {
            userNotesRef.document(existingNoteId!!)
                .set(note) // Offline-supported write
                .addOnSuccessListener {
                    Toast.makeText(this, "Note Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e -> showErrorPopup("Update Failed: ${e.message}") }
        } else {
            userNotesRef
                .add(note) // Offline-supported write
                .addOnSuccessListener {
                    Toast.makeText(this, "Note Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e -> showErrorPopup("Save Failed: ${e.message}") }
        }
    }

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
                runOnUiThread { progressBar.visibility = View.GONE; btnGenerate.isEnabled = true; showErrorPopup("Network Error") }
            }
            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                    if (!response.isSuccessful) { showErrorPopup("AI Error"); return@runOnUiThread }
                    try {
                        val gson = Gson()
                        val map = gson.fromJson(resBody, Map::class.java)
                        val candidates = map["candidates"] as? List<*>
                        if (candidates.isNullOrEmpty()) return@runOnUiThread
                        val content = (candidates[0] as Map<*, *>)["content"] as Map<*, *>
                        val parts = (content["parts"] as List<*>)[0] as Map<*, *>
                        var aiText = parts["text"] as String
                        aiText = aiText.replace("```json", "").replace("```", "").trim()
                        val generatedNote = gson.fromJson(aiText, Note::class.java)
                        etTitle.setText(generatedNote.title)
                        etCategory.setText(generatedNote.category)
                        etPriority.setText(generatedNote.priority.toString())
                        etAiSummary.setText(generatedNote.aiSummary)
                        etFinalNote.setText(generatedNote.finalNote)
                        setSplitTime(generatedNote.reminderTime)
                    } catch (e: Exception) {
                        etFinalNote.setText(userText)
                    }
                }
            }
        })
    }
}