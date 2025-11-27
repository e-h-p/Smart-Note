package com.example.notesmarterai.model

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

    // New Field: Captures the time the note object is created
    var timestamp: Long = System.currentTimeMillis(),

    // ID is excluded from AI generation, used for Firebase
    var id: String = ""
) {
    // Empty constructor required for Firestore
    constructor() : this("", "", "", 1, "", "", System.currentTimeMillis(), "")
}