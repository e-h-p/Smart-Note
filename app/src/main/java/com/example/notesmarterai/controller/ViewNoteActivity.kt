package com.example.notesmarterai.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.notesmarterai.R
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewNoteActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDateCat: TextView
    private lateinit var tvOriginal: TextView
    private lateinit var tvFinal: TextView   // NEW
    private lateinit var tvSummary: TextView
    private lateinit var btnEdit: ImageView
    private lateinit var toolbar: MaterialToolbar

    // Data Holders
    private var noteId: String? = null
    private var noteTitle: String? = null
    private var noteCategory: String? = null
    private var notePriority: Int = 1
    private var noteFinal: String? = null
    private var noteSummary: String? = null
    private var noteRough: String? = null
    private var noteReminder: String? = null
    private var noteTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_note)

        initViews()
        getIntentData()
        setDataToViews()

        // Edit Action
        btnEdit.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            intent.putExtra("NOTE_ID", noteId)
            intent.putExtra("NOTE_TITLE", noteTitle)
            intent.putExtra("NOTE_CATEGORY", noteCategory)
            intent.putExtra("NOTE_PRIORITY", notePriority)
            intent.putExtra("NOTE_REMINDER", noteReminder)
            intent.putExtra("NOTE_FINAL", noteFinal)
            intent.putExtra("NOTE_SUMMARY", noteSummary)
            intent.putExtra("NOTE_ROUGH", noteRough)
            intent.putExtra("NOTE_TIMESTAMP", noteTimestamp)
            startActivity(intent)
            finish()
        }

        // Back Action
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvViewTitle)
        tvDateCat = findViewById(R.id.tvViewDateCat)
        tvOriginal = findViewById(R.id.tvViewOriginal)
        tvFinal = findViewById(R.id.tvViewFinal) // NEW
        tvSummary = findViewById(R.id.tvViewSummary)
        btnEdit = findViewById(R.id.btnEdit)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun getIntentData() {
        val i = intent
        noteId = i.getStringExtra("NOTE_ID")
        noteTitle = i.getStringExtra("NOTE_TITLE")
        noteCategory = i.getStringExtra("NOTE_CATEGORY")
        notePriority = i.getIntExtra("NOTE_PRIORITY", 1)
        noteReminder = i.getStringExtra("NOTE_REMINDER")
        noteFinal = i.getStringExtra("NOTE_FINAL")
        noteSummary = i.getStringExtra("NOTE_SUMMARY")
        noteRough = i.getStringExtra("NOTE_ROUGH")
        noteTimestamp = i.getLongExtra("NOTE_TIMESTAMP", 0)
    }

    private fun setDataToViews() {
        tvTitle.text = noteTitle

        // Date Format
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(noteTimestamp))
        tvDateCat.text = "$noteCategory • $dateStr"

        // 1. Original Note
        if (!noteRough.isNullOrEmpty()) {
            tvOriginal.text = noteRough
        } else {
            tvOriginal.text = "No original input."
        }

        // 2. AI Rewritten Note
        if (!noteFinal.isNullOrEmpty()) {
            tvFinal.text = noteFinal
        } else {
            tvFinal.text = "Not generated yet."
        }

        // 3. AI Summary
        if (!noteSummary.isNullOrEmpty()) {
            tvSummary.text = noteSummary
        } else {
            tvSummary.text = "No summary available."
        }
    }
}