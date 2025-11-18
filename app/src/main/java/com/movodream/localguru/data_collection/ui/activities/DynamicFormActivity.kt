package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.base.BaseActivity
import com.google.android.material.card.MaterialCardView
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.MockNetwork
import com.movodream.localguru.data_collection.presentation.FormViewModel
import com.movodream.localguru.data_collection.ui.adapter.DynamicFormAdapter
import com.movodream.localguru.data_collection.ui.adapter.TabAdapter
import kotlin.collections.indexOfFirst


class DynamicFormActivity : BaseActivity() {

    private lateinit var vm: FormViewModel
    private lateinit var tabRecycler: RecyclerView
    private lateinit var tabAdapter: TabAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DynamicFormAdapter
    private lateinit var titleTv: AppCompatTextView
    private lateinit var progressTv: AppCompatTextView
    private lateinit var categoryTV: AppCompatTextView
    private lateinit var saveBtn: MaterialCardView
    private lateinit var submitBtn: MaterialCardView

    private var currentPhotoFieldId: String? = null
    private var lastShownTabId: String? = null

    // Gallery picker
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty() && currentPhotoFieldId != null) {
                vm.addPhotoUris(currentPhotoFieldId!!, uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dynamic_form)

        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[FormViewModel::class.java]

        titleTv = findViewById(R.id.title)
        progressTv = findViewById(R.id.progress_text)
        categoryTV = findViewById(R.id.tv_category)
        tabRecycler = findViewById(R.id.tab_recycler)
        recycler = findViewById(R.id.recycler)
        saveBtn = findViewById(R.id.cv_btn_save)
        submitBtn = findViewById(R.id.cv_btn_submit)

        // Tab recycler setup
        tabAdapter = TabAdapter { tab -> showTab(tab.id) }
        tabRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        tabRecycler.adapter = tabAdapter
        tabRecycler.setHasFixedSize(true)

        // Form recycler setup
        recycler.layoutManager = object : LinearLayoutManager(this) {
            override fun onRequestChildFocus(
                parent: RecyclerView,
                state: RecyclerView.State,
                child: View,
                focused: View?
            ): Boolean = true // Prevent focus jump
        }
        recycler.setItemViewCacheSize(50)
        recycler.isNestedScrollingEnabled = false

        adapter = DynamicFormAdapter(
            onTakePhoto = { /* Camera disabled */ },
            onPickImages = { fieldId -> requestGalleryAndPick(fieldId) },
            onRequestLocation = { fieldId -> vm.fetchLocation(this, fieldId) },
            onFieldChanged = { id, value -> vm.updateValue(id, value) },
            onRemovePhoto = { fieldId, uri -> vm.removePhotoUri(fieldId, uri) }
        )
        adapter.setHasStableIds(true)
        recycler.adapter = adapter

        // Observe schema
        vm.schemaLive.observe(this) { schema ->
            schema?.let {
                titleTv.text = it.title
                progressTv.text = "${it.progress ?: 0}%"
                categoryTV.text = it.tags[0]
                tabAdapter.submitTabs(it.tabs)
                it.tabs.firstOrNull()?.let { tab -> showTab(tab.id) }
            }
        }

        vm.fieldChangeLive.observe(this) { fieldId ->
            if (lastShownTabId == null) return@observe
            val pos = adapterPositionForField(fieldId)
            if (pos >= 0) adapter.notifyItemChanged(pos)
        }

        // Save draft + move to next tab
        saveBtn.setOnClickListener {
            val schema = vm.schemaLive.value ?: return@setOnClickListener
            val currentTabId = lastShownTabId ?: return@setOnClickListener

            val errors = vm.validateTab(currentTabId)
            if (errors.isNotEmpty()) {
                Toast.makeText(this, errors.values.first(), Toast.LENGTH_LONG).show()
                adapter.setErrors(errors)
                return@setOnClickListener
            }

            vm.saveDraftForTab(currentTabId)

            val tabs = schema.tabs
            val idx = tabs.indexOfFirst { it.id == currentTabId }
            if (idx in 0 until tabs.lastIndex) {
                val nextTab = tabs[idx + 1]
                lastShownTabId = nextTab.id
                tabAdapter.highlightTab(nextTab.id)
                showTab(nextTab.id)
                Toast.makeText(this, "Draft saved. Moving to ${nextTab.title}", Toast.LENGTH_SHORT)
                    .show()
            } else {
                val payload = vm.buildPayload()
                vm.submit(payload)

            }
        }

        // Submit entire form
        submitBtn.setOnClickListener {
            val schema = vm.schemaLive.value ?: return@setOnClickListener
            val allErrors = mutableMapOf<String, String>()
            schema.tabs.forEach { t -> allErrors.putAll(vm.validateTab(t.id)) }
            if (allErrors.isNotEmpty()) {
                Toast.makeText(this, allErrors.values.first(), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val payload = vm.buildPayload()
            vm.submit(payload)
            Toast.makeText(this, "Data Saved Successfully...", Toast.LENGTH_SHORT)
                .show()
            finish()
        }

        // Load schema from backend mock
        var s = "";
        if(intent.getIntExtra("KEY_ID",1)==2){
           s  = MockNetwork.fetchSchemaString2()
        }else{
           s   = MockNetwork.fetchSchemaString()
        }

        vm.loadSchemaFromString(s)
    }

    private fun showTab(tabId: String) {
        lastShownTabId = tabId
        val schema = vm.schemaLive.value ?: return
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return
        adapter.submitFields(tab.fields, vm.valuesLive.value ?: emptyMap())
        val pos = schema.tabs.indexOf(tab)
        if (pos >= 0) tabRecycler.smoothScrollToPosition(pos)
    }

    /**
     * Request correct gallery permission for all Android versions.
     */
    private fun requestGalleryAndPick(fieldId: String) {
        currentPhotoFieldId = fieldId

        // ✅ Android 13 (API 33) and above: no need for manual permission with GetMultipleContents()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickImages.launch("image/*")
            return
        }

        // ✅ For Android 12 and below, we still need READ_EXTERNAL_STORAGE
        val perm = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 3002)
            return
        }

        pickImages.launch("image/*")
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 3002) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                currentPhotoFieldId?.let { requestGalleryAndPick(it) }
            } else {
                Toast.makeText(
                    this,
                    "Gallery permission required to upload photos",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun adapterPositionForField(fieldId: String): Int {
        val fields = adapter.getCurrentFields()
        return fields.indexOfFirst { it.id == fieldId }
    }

}
