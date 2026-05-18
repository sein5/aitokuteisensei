package com.example.aitokuteisensei.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("selected_language")
        val KEY_ONBOARDED = booleanPreferencesKey("is_onboarded")
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "en" // Default to English
    }

    val isOnboardedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDED] ?: false
    }

    suspend fun saveLanguage(langCode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = langCode }
    }

    suspend fun setOnboarded(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDED] = completed }
    }
}