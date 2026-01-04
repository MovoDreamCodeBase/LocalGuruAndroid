package com.movodream.localguru.data_collection.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PhotoWithMeta(
    val uri: String,
    var label: String = "",
    var description: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
) : Parcelable


