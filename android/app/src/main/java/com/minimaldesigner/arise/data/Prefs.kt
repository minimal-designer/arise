package com.minimaldesigner.arise.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minimaldesigner.arise.core.DEFAULT_REMINDER_TIME
import com.minimaldesigner.arise.ui.theme.AccentPref
import com.minimaldesigner.arise.ui.theme.NumStyle
import com.minimaldesigner.arise.ui.theme.ThemePref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.store by preferencesDataStore("arise")

data class Settings(
    val theme: ThemePref = ThemePref.SYSTEM,
    val numbers: NumStyle = NumStyle.DOT,
    val accent: AccentPref = AccentPref.ORANGE,
    /** Confetti on a tick and when the day clears (haptics stay either way). */
    val celebrate: Boolean = true,
    /** The Meditate task's app, as a package name; empty until one is picked. */
    val meditateApp: String = "",
    /** The evening reminder: off until turned on in Settings (it needs notification permission). */
    val reminderOn: Boolean = false,
    val reminderAt: LocalTime = DEFAULT_REMINDER_TIME,
)

class Prefs(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val numbersKey = stringPreferencesKey("numbers")
    private val accentKey = stringPreferencesKey("accent")
    private val celebrateKey = booleanPreferencesKey("celebrate")
    private val meditateKey = stringPreferencesKey("meditate_app")
    private val reminderOnKey = booleanPreferencesKey("reminder_on")
    private val reminderAtKey = stringPreferencesKey("reminder_at")

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            theme = p[themeKey]?.let { v -> ThemePref.entries.firstOrNull { it.name == v } } ?: ThemePref.SYSTEM,
            numbers = p[numbersKey]?.let { v -> NumStyle.entries.firstOrNull { it.name == v } } ?: NumStyle.DOT,
            accent = p[accentKey]?.let { v -> AccentPref.entries.firstOrNull { it.name == v } } ?: AccentPref.ORANGE,
            meditateApp = p[meditateKey] ?: "",
            celebrate = p[celebrateKey] ?: true,
            reminderOn = p[reminderOnKey] ?: false,
            reminderAt = p[reminderAtKey]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: DEFAULT_REMINDER_TIME,
        )
    }

    suspend fun setTheme(t: ThemePref) {
        context.store.edit { it[themeKey] = t.name }
    }

    suspend fun setNumbers(n: NumStyle) {
        context.store.edit { it[numbersKey] = n.name }
    }

    suspend fun setAccent(a: AccentPref) {
        context.store.edit { it[accentKey] = a.name }
    }

    suspend fun setCelebrate(on: Boolean) {
        context.store.edit { it[celebrateKey] = on }
    }

    suspend fun setMeditateApp(pkg: String) {
        context.store.edit { it[meditateKey] = pkg }
    }

    suspend fun setReminder(on: Boolean, at: LocalTime) {
        context.store.edit {
            it[reminderOnKey] = on
            it[reminderAtKey] = at.toString()
        }
    }

    /** 1.2.0 dropped the server food sync: forget its address and token. */
    suspend fun dropFoodServer() {
        context.store.edit {
            it.remove(stringPreferencesKey("nas_url"))
            it.remove(stringPreferencesKey("nas_token"))
        }
    }
}
