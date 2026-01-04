package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.base.BaseActivity
import com.google.android.gms.location.LocationServices
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.PhotoWithMeta
import com.movodream.localguru.data_collection.ui.adapter.PhotoMetadataAdapter

class PhotoMetadataActivity : BaseActivity() {

    private lateinit var adapter: PhotoMetadataAdapter
    private val list = mutableListOf<PhotoWithMeta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_metadata)

        val fieldId = intent.getStringExtra("fieldId") ?: run {
            finish()
            return
        }

        val incoming =
            intent.getParcelableArrayListExtra<PhotoWithMeta>("photos") ?: arrayListOf()

        adapter = PhotoMetadataAdapter { item ->
            val newList = adapter.currentList.toMutableList()
            newList.remove(item)
            adapter.submitList(newList)
        }

        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@PhotoMetadataActivity)
            adapter = this@PhotoMetadataActivity.adapter
            setHasFixedSize(true)
        }

        adapter.submitList(incoming.toList())

// Location update
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val updated = adapter.currentList.map {
                        it.copy(latitude = loc.latitude, longitude = loc.longitude)
                    }
                    adapter.submitList(updated)
                }
            }
        }

        findViewById<TextView>(R.id.btnSave).setOnClickListener {

            val currentList = adapter.currentList

            val invalidItem = currentList.firstOrNull {
                it.label.trim().isEmpty() || it.description.trim().isEmpty()
            }

            if (invalidItem != null) {
                Toast.makeText(
                    this,
                    "Please enter label and description for all photos",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val data = Intent().apply {
                putExtra("fieldId", fieldId)
                putParcelableArrayListExtra("result", ArrayList(currentList))
            }

            setResult(RESULT_OK, data)
            finish()
        }


    }
}
