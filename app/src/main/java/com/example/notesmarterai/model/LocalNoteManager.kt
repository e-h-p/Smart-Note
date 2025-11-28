package com.example.notesmarterai.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalNoteManager {
    private const val PREF_NAME = "GuestNotes"
    private const val KEY_NOTES = "notes_json"
    private val gson = Gson()

    fun saveNote(context: Context, note: Note) {
        val notes = getNotes(context).toMutableList()

        if (note.id.isEmpty()) {
            note.id = "guest_${System.currentTimeMillis()}"
            notes.add(note)
        } else {
            val index = notes.indexOfFirst { it.id == note.id }
            if (index != -1) {
                notes[index] = note
            } else {
                notes.add(note)
            }
        }
        saveList(context, notes)
    }

    fun deleteNote(context: Context, noteId: String) {
        val notes = getNotes(context).toMutableList()
        notes.removeAll { it.id == noteId }
        saveList(context, notes)
    }

    fun getNotes(context: Context): List<Note> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        val type = object : TypeToken<List<Note>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveList(context: Context, notes: List<Note>) {
        val json = gson.toJson(notes)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTES, json)
            .apply()
    }
}