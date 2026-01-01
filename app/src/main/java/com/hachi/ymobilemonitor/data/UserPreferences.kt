package com.hachi.ymobilemonitor.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class UserPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKey.ENC_AES256_SIV,
        EncryptedSharedPreferences.PrefValue.ENC_AES256_GCM
    )

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PASSWORD = "password"
    }

    fun saveCredentials(id: String, pass: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_PASSWORD, pass)
            .apply()
    }

    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)
    fun getPassword(): String? = sharedPreferences.getString(KEY_PASSWORD, null)

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
