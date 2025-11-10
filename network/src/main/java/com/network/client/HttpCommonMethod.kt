package com.network.client

import com.core.preferences.MyPreference
import com.core.preferences.PrefKey


object HttpCommonMethod {


    /**
     * get Authorized token
     */
    fun getToken(): String? {
        return MyPreference.getValueString(
            PrefKey.ACCESS_TOKEN,
            ""
        )
    }


}
