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
    var finalNote: String = "", // This will store the "AI Rewritten" version

    @SerializedName("AiSummary")
    var aiSummary: String = "",

    // NEW: Store the original rough input so it is never lost
    @SerializedName("RoughNote")
    var roughNote: String = "",

    var timestamp: Long = System.currentTimeMillis(),
    var id: String = ""
) {
    constructor() : this("", "", "", 1, "", "", "", System.currentTimeMillis(), "")
}