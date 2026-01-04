package com.data.remote.model
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
@Parcelize
data class RevisionDataResponse(
    val poiId: String,
    val agentId: String,
    val poiDetails: String,   // JSON string (parse separately)
    val galleryPhotos: String,   // JSON string (parse separately)
    val createdDate: String,
    val subPoiRecords: List<SubPoiRecord>
): Parcelable{
    @Parcelize
    data class SubPoiRecord(
        val poiId: String,
        val agentId: String,

        // JSON STRING (needs manual parsing)
        val subpoi_details: String,

        val created_date: String,

        // JSON STRING
        val subpoi_galleryphotos: String,

        val subpoiId: String
    ): Parcelable

}
