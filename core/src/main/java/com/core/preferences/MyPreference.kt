package com.core.preferences

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.core.preferences.PrefKey

object MyPreference {

    private var mSharedPref: SharedPreferences? = null
    private var rememberSharedPref: SharedPreferences? = null


    fun init(context: Context?) {

        context?.let { _context ->

            if (mSharedPref == null)
                mSharedPref =
                    _context.getSharedPreferences(PrefKey.PREFERENCE_NAME, Activity.MODE_PRIVATE)

            if (rememberSharedPref == null)
                rememberSharedPref =
                    _context.getSharedPreferences(
                        PrefKey.REMEMBER_PREFERENCE_NAME,
                        Activity.MODE_PRIVATE
                    )
        }
    }

    @JvmStatic
    fun getValueString(
        key: String,
        defaultValue: String
    ): String? {
        return mSharedPref?.getString(key, defaultValue)
    }

    fun setValueString(key: String, value: String) {
        val prefsPrivateEditor = mSharedPref?.edit()
        prefsPrivateEditor?.putString(key, value)
        prefsPrivateEditor?.apply()
    }

    fun getValueBoolean(
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return mSharedPref!!.getBoolean(key, defaultValue)
    }

    fun setValueBoolean(key: String, value: Boolean) {
        val prefsPrivateEditor = mSharedPref?.edit()
        prefsPrivateEditor?.putBoolean(key, value)
        prefsPrivateEditor?.apply()
    }

    fun getRememberValueBoolean(
        key: String,
        defaultValue: Boolean
    ): Boolean? {
        return rememberSharedPref?.getBoolean(key, defaultValue)
    }

    fun setRememberValueBoolean(key: String, value: Boolean) {
        val prefsPrivateEditor = rememberSharedPref?.edit()
        prefsPrivateEditor?.putBoolean(key, value)
        prefsPrivateEditor?.apply()
    }

    fun getRememberValueString(
        key: String,
        defaultValue: String
    ): String? {
        return rememberSharedPref?.getString(key, defaultValue)
    }

    fun setRememberValueString(key: String, value: String) {
        val prefsPrivateEditor = rememberSharedPref?.edit()
        prefsPrivateEditor?.putString(key, value)
        prefsPrivateEditor?.apply()
    }

    fun getValueInt(
        key: String,
        defaultValue: Int
    ): Int {
        return mSharedPref!!.getInt(key, defaultValue)
    }

    fun setValueInt(key: String, value: Int) {
        val prefsPrivateEditor = mSharedPref?.edit()
        prefsPrivateEditor?.putInt(key, value)
        prefsPrivateEditor?.apply()
    }


    fun setValues(context: Context, key: String, value: Any) {
        init(context)
        val prefsPrivateEditor = mSharedPref!!.edit()
        when (value) {
            is String -> prefsPrivateEditor.putString(key, value)
            is Boolean -> prefsPrivateEditor.putBoolean(key, value)
            is Int -> prefsPrivateEditor.putInt(key, value)

            is Float -> prefsPrivateEditor.putFloat(key, value)

            is Long -> prefsPrivateEditor.putLong(key, value)

        }
        prefsPrivateEditor.apply()
        //mSharedPref = null
    }

    fun clearAllData() {
        val prefsPrivateEditor = mSharedPref?.edit()
        prefsPrivateEditor?.clear()
        prefsPrivateEditor?.apply()
    }
}