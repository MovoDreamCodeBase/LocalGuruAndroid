package com.movodream.localguru.data_collection.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.base.BaseActivity
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.ui.adapter.PhotosAdapter
import com.movodream.localguru.databinding.ActivityPoiDetailsBinding
import com.movodream.localguru.databinding.ItemDetailRowBinding
import org.json.JSONArray
import org.json.JSONObject

class PoiDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityPoiDetailsBinding
    private val shareBuilder = StringBuilder()
    private  var selectedPOI: TaskItem? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoiDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initData()
        setCustomListener()
    }



    private fun initData() {
        // API gives poiDetails as a JSON string
        val poiDetailsRaw = intent.getStringExtra("poiDetails") ?: return
        val galleryRaw = intent.getStringExtra("galleryPhotos") ?: ""
        selectedPOI =
            intent.getParcelableExtra("KEY_POI") as TaskItem?
        binding.tvAgentName.text = selectedPOI!!.agentName
        binding.tvAgentLocation.text = selectedPOI!!.agentLocation +" Data Collector"
        if (selectedPOI!!.agentName.isNullOrBlank()) {
            binding.tvAgentProfile.text = ""

        }else {

            val parts = selectedPOI!!.agentName.trim().split(" ")

            val initials = parts
                .filter { it.isNotBlank() }
                .map { it.first().uppercaseChar() }
                .take(2)     // only first 2 characters (J + D)
                .joinToString("")

            binding.tvAgentProfile.text  = initials
        }
        val poiDetailsJson = parsePoiDetailsSafely(poiDetailsRaw)

        renderPoiDetails(poiDetailsJson)

        // loadGalleryImages(galleryRaw,poiDetailsRaw)
        handleGalleryPhotos(poiDetailsJson,galleryRaw)
    }

    private fun handleGalleryPhotos(full: JSONObject, oldJson: String) {

        if (full.has("GalleryPhotos")) {
            val any = full.opt("GalleryPhotos")

            when (any) {

                is JSONArray -> {
                    // DIRECT ARRAY CASE
                    val urls = mutableListOf<String>()
                    for (i in 0 until any.length()) {
                        val url = any.optString(i)
                        if (url.startsWith("http")) urls.add(url)
                    }

                    if (urls.isNotEmpty()) {
                        showGallery(urls)
                        return
                    }
                }

                is String -> {
                    // STRING RAW LIST CASE
                    val raw = any.trim()
                    if (raw.isNotBlank() && raw != "[]") {
                        if (raw.contains("http", ignoreCase = true)) {
                            loadNewGalleryPhotos(raw)
                            return
                        }
                    }
                }
            }
        }

        // FALLBACK → OLD FORMAT
        loadGalleryImages(oldJson)
    }


    private fun loadNewGalleryPhotos(raw: String) {
        val cleaned = raw
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("[")
            .removeSuffix("]")
            .replace("\\\"", "")
            .trim()

        if (cleaned.isBlank()) return

        val urls = cleaned.split(",")
            .map { it.trim() }
            .filter { it.startsWith("http") }

        if (urls.isEmpty()) return

        showGallery(urls)
    }
    private fun showGallery(urls: List<String>) {
        binding.tvImagesTitle.visibility = View.VISIBLE
        binding.rvImages.visibility = View.VISIBLE

        binding.rvImages.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        binding.rvImages.adapter = PhotosAdapter(urls)
        binding.rvImages.isNestedScrollingEnabled = false
    }


    private fun parsePoiDetailsSafely(raw: String): JSONObject {
        return try {
            // if it's already pure JSON
            JSONObject(raw)
        } catch (e: Exception) {
            // if it's a JSON string with escaped quotes
            val cleaned = raw
                .trim()
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\"", "\"")
            JSONObject(cleaned)
        }
    }

    // ====== Dynamic rendering with NO hardcoded keys ======

    private fun renderPoiDetails(jsonObject: JSONObject) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()

            // Skip Gallery Photos from details list
            if (key.equals("GalleryPhotos", ignoreCase = true)) continue

            val value = jsonObject.opt(key)
            addFieldFromAny(key, value)
        }
    }


    private fun addFieldFromAny(key: String, value: Any?) {
        if (value == null) return

        when (value) {
            is String -> {
                val v = value.trim()
                if (v.isNotEmpty()) {
                    addTextField(keyToLabel(key), formatValue(v))
                }
            }
            is Number, is Boolean -> {
                val str = value.toString()
                if (str.isNotEmpty()) {
                    addTextField(keyToLabel(key), str)
                }
            }
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    when (item) {
                        is String -> {
                            val t = item.trim()
                            if (t.isNotEmpty()) list.add(formatValue(t))
                        }
                        is Number, is Boolean -> {
                            list.add(item.toString())
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    val bullets = list.joinToString("\n") { "• $it" }
                    addTextField(keyToLabel(key), bullets)
                }
            }
            is JSONObject -> {
                // Optional: flatten nested objects if you want
            }
        }
    }

    // Use ViewBinding for row items
    private fun addTextField(label: String, value: String?) {
        if (value.isNullOrBlank()) return

        val rowBinding = ItemDetailRowBinding.inflate(
            layoutInflater,
            binding.containerFields,
            false
        )
        rowBinding.tvLabel.text = "$label :"
        rowBinding.tvValue.text = value.trim()
        binding.containerFields.addView(rowBinding.root)
        shareBuilder.append("$label : $value\n")
    }

    // ====== Helpers: label + value formatting ======

    private fun keyToLabel(key: String): String {
        val snakeFixed = key.replace("_", " ")
        val camelFixed = snakeFixed.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

        return camelFixed
            .split(" ", "-", ignoreCase = true)
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
    }

    private fun formatValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed

        // Detect URLs or Email — return as is
        if (trimmed.contains("://") || trimmed.contains("@") || trimmed.startsWith("www.", true)) {
            return trimmed // DO NOT FORMAT URL / EMAIL
        }

        // Normal text formatting starts here
        val text = trimmed.replace("_", " ").lowercase()

        val abbreviations = setOf("qr", "upi", "nfc", "gps", "atm")

        val words = text.split(" ")

        val formatted = words.mapIndexed { index, word ->
            val w = word.trim()

            when {
                abbreviations.contains(w) -> w.uppercase()
                index == 0 -> w.replaceFirstChar { it.titlecase() }
                else -> w.lowercase()
            }
        }.joinToString(" ")

        return formatted.replace(Regex("([.!?])\\s*(\\w)")) { match ->
            match.groupValues[1] + " " + match.groupValues[2].uppercase()
        }
    }

    // ====== Gallery using ViewBinding in adapter ======

    private fun loadGalleryImages(json: String) {
        if (json.isBlank()) return

        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return
        }
        if (arr.length() == 0) return

        val urls = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("img_url")
            if (url.isNotBlank()) urls.add(url)
        }
        if (urls.isEmpty()) return

        binding.tvImagesTitle.visibility = View.VISIBLE
        binding.rvImages.visibility = View.VISIBLE

        binding.rvImages.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.rvImages.adapter = PhotosAdapter(urls)

        binding.rvImages.isNestedScrollingEnabled = false
    }

    private fun setCustomListener() {
        binding.cvBtnShare.setOnClickListener {
            sharePoiDetails()
        }

    }

    private fun sharePoiDetails() {
        val textToShare = shareBuilder.toString().trim()

        if (textToShare.isBlank()) {
            Toast.makeText(this, "No details available to share", Toast.LENGTH_SHORT).show()
            return
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }

        val chooser = Intent.createChooser(sendIntent, "Share POI Details")
        startActivity(chooser)
    }

}
