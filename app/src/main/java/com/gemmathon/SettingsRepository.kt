package com.gemmathon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gemmathon_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val RANDOM_SEED = intPreferencesKey("random_seed")
        val USE_FIXED_SEED = booleanPreferencesKey("use_fixed_seed")
        val MAX_ATTEMPTS = intPreferencesKey("max_attempts")
        val BACKEND = stringPreferencesKey("backend")
        val CODE_GEN_PROMPT = stringPreferencesKey("code_gen_prompt")
        val EVAL_PROMPT = stringPreferencesKey("eval_prompt")
        val REWRITE_PROMPT = stringPreferencesKey("rewrite_prompt")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            temperature = prefs[Keys.TEMPERATURE] ?: 0.1f,
            topK = prefs[Keys.TOP_K] ?: 3,
            topP = prefs[Keys.TOP_P] ?: 0.9f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
            randomSeed = prefs[Keys.RANDOM_SEED] ?: 101,
            useFixedSeed = prefs[Keys.USE_FIXED_SEED] ?: false,
            maxAttempts = prefs[Keys.MAX_ATTEMPTS] ?: 5,
            backend = prefs[Keys.BACKEND]?.let { InferenceBackend.valueOf(it) } ?: InferenceBackend.CPU,
            codeGenPrompt = prefs[Keys.CODE_GEN_PROMPT] ?: DEFAULT_CODE_GEN_PROMPT,
            evalPrompt = prefs[Keys.EVAL_PROMPT] ?: DEFAULT_EVAL_PROMPT,
            rewritePrompt = prefs[Keys.REWRITE_PROMPT] ?: DEFAULT_REWRITE_PROMPT
        )
    }

    suspend fun update(s: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEMPERATURE] = s.temperature
            prefs[Keys.TOP_K] = s.topK
            prefs[Keys.TOP_P] = s.topP
            prefs[Keys.MAX_TOKENS] = s.maxTokens
            prefs[Keys.RANDOM_SEED] = s.randomSeed
            prefs[Keys.USE_FIXED_SEED] = s.useFixedSeed
            prefs[Keys.MAX_ATTEMPTS] = s.maxAttempts
            prefs[Keys.BACKEND] = s.backend.name
            prefs[Keys.CODE_GEN_PROMPT] = s.codeGenPrompt
            prefs[Keys.EVAL_PROMPT] = s.evalPrompt
            prefs[Keys.REWRITE_PROMPT] = s.rewritePrompt
        }
    }
}
