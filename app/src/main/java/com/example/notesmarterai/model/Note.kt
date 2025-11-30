package com.example.notesmarterai.model

import com.google.firebase.firestore.Exclude
import com.google.gson.annotations.SerializedName

data class Note(
    @SerializedName("Title")
    var title: String = "",

    @SerializedName("Category")
    var category: String = "",

    @SerializedName("ReminderTime")
    var reminderTime: String = "",

    @SerializedName("Priority")
    var priority: Int = 1,

    @SerializedName("FinalNote")
    var finalNote: String = "",

    @SerializedName("AiSummary")
    var aiSummary: String = "",

    @SerializedName("RoughNote")
    var roughNote: String = "",

    var timestamp: Long = System.currentTimeMillis(),

    // NEW: Pin feature
    var isPinned: Boolean = false,

    var id: String = ""
) {
    // Exclude from Firestore/GSON so we don't save selection state permanently
    @get:Exclude
    var isSelected: Boolean = false

    constructor() : this("", "", "", 1, "", "", "", System.currentTimeMillis(), false, "")
}