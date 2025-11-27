package com.example.notesmarterai.controller

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.notesmarterai.R
import com.example.notesmarterai.model.Note
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AddNoteActivity : AppCompatActivity() {

    private lateinit var etNoteInput: EditText
    private lateinit var etTitle: EditText
    private lateinit var etCategory: EditText
    private lateinit var etPriority: EditText
    private lateinit var etReminder: EditText
    private lateinit var etFinalNote: EditText
    private lateinit var etAiSummary: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGenerate: Button
    private lateinit var btnSave: Button

    // Header text view to change "Create Note" to "Edit Note"
    private lateinit var tvHeaderTitle: TextView

    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // REPLACE WITH YOUR KEY
    private val apiKey = "Gemini API KEY"

    // EDIT MODE FLAGS
    private var isEditMode = false
    private var existingNoteId: String? = null
    private var existingTimestamp: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        initViews()

        // <--- NEW: CHECK FOR EDIT MODE --->
        if (intent.hasExtra("NOTE_ID")) {
            isEditMode = true
            existingNoteId = intent.getStringExtra("NOTE_ID")
            existingTimestamp = intent.getLongExtra("NOTE_TIMESTAMP", System.currentTimeMillis())

            // 1. Change UI to look like "Edit Mode"
            // Note: If you don't have an ID for the header TextView in XML,
            // you can skip the next line or add android:id="@+id/tvHeaderTitle" to the TextView in activity_add_note.xml
            // tvHeaderTitle.text = "Edit Note"
            btnSave.text = "Update Note"

            // 2. Pre-fill the fields
            etTitle.setText(intent.getStringExtra("NOTE_TITLE"))
            etCategory.setText(intent.getStringExtra("NOTE_CATEGORY"))
            etPriority.setText(intent.getIntExtra("NOTE_PRIORITY", 1).toString())
            etReminder.setText(intent.getStringExtra("NOTE_REMINDER"))
            etFinalNote.setText(intent.getStringExtra("NOTE_FINAL"))
            etAiSummary.setText(intent.getStringExtra("NOTE_SUMMARY"))
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
        etReminder = findViewById(R.id.etReminder)
        etFinalNote = findViewById(R.id.etFinalNote)
        etAiSummary = findViewById(R.id.etAiSummary)
        progressBar = findViewById(R.id.progressBarAI)
        btnGenerate = findViewById(R.id.btnGenerateAI)
        btnSave = findViewById(R.id.btnSaveNote)
        // tvHeaderTitle = findViewById(R.id.tvHeaderTitle) // Uncomment if you added ID to XML
    }

    private fun saveNoteToFirebase() {
        val note = Note(
            title = etTitle.text.toString(),
            category = etCategory.text.toString(),
            reminderTime = etReminder.text.toString(),
            priority = etPriority.text.toString().toIntOrNull() ?: 1,
            finalNote = etFinalNote.text.toString(),
            aiSummary = etAiSummary.text.toString(),
            timestamp = existingTimestamp // Preserve original time if editing
        )

        if (isEditMode && existingNoteId != null) {
            // <--- UPDATE EXISTING NOTE --->
            db.collection("notes").document(existingNoteId!!)
                .set(note) // .set() overwrites the existing document
                .addOnSuccessListener {
                    Toast.makeText(this, "Note Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Update Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // <--- CREATE NEW NOTE --->
            db.collection("notes")
                .add(note)
                .addOnSuccessListener {
                    Toast.makeText(this, "Note Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generateNoteWithGemini(userText: String) {
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$apiKey"

        val promptText = """
            Analyze the following text and extract/generate a JSON object.
            Text: "$userText"
            
            Schema:
            {
                "Title": "string",
                "Category": "string",
                "ReminderTime": "string",
                "Priority": integer (1-10),
                "FinalNote": "string (cleaned up version of input)",
                "AiSummary": "string"
            }
            Return ONLY the JSON. No markdown.
        """.trimIndent()

        val jsonRequest = """
            {
              "contents": [{ "parts": [{ "text": ${Gson().toJson(promptText)} }] }]
            }
        """.trimIndent()

        val body = jsonRequest.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    Toast.makeText(this@AddNoteActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    try {
                        val gson = Gson()
                        val map = gson.fromJson(resBody, Map::class.java)
                        val candidates = map["candidates"] as List<*>
                        val content = (candidates[0] as Map<*, *>)["content"] as Map<*, *>
                        val parts = (content["parts"] as List<*>)[0] as Map<*, *>
                        var aiText = parts["text"] as String
                        aiText = aiText.replace("```json", "").replace("```", "").trim()

                        val generatedNote = gson.fromJson(aiText, Note::class.java)

                        etTitle.setText(generatedNote.title)
                        etCategory.setText(generatedNote.category)
                        etPriority.setText(generatedNote.priority.toString())
                        etReminder.setText(generatedNote.reminderTime)
                        etFinalNote.setText(generatedNote.finalNote)
                        etAiSummary.setText(generatedNote.aiSummary)

                    } catch (e: Exception) {
                        Log.e("Gemini", "Parse Error", e)
                        etFinalNote.setText("Raw Response (Parse Error): $resBody")
                    }
                }
            }
        })
    }
}