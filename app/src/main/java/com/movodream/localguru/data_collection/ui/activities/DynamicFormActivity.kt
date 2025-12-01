package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.base.BaseActivity
import com.core.customviews.CustomDialogBuilder
import com.core.utils.DebugLog
import com.core.utils.PermissionUtils
import com.core.utils.Utils
import com.google.android.material.card.MaterialCardView
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.FormViewModel
import com.movodream.localguru.data_collection.ui.adapter.DynamicFormAdapter
import com.movodream.localguru.data_collection.ui.adapter.TabAdapter
import com.network.client.ResponseHandler
import com.network.model.ResponseData
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
    private var poiId = -1
    private  var selectedPOI: TaskItem? = null

    private var pendingLatField = ""
    private var pendingLngField = ""

    private lateinit var permissionUtils: PermissionUtils

    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )


        permissions.toTypedArray()
    }
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
        val schema : FormSchema? = intent.getParcelableExtra("KEY_SCHEMA") as FormSchema?
        selectedPOI =
            intent.getParcelableExtra("KEY_POI") as TaskItem?
        selectedPOI?.let { poiId = it.poiId }
        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[FormViewModel::class.java]
        permissionUtils = PermissionUtils(this)
        titleTv = findViewById(R.id.title)
        progressTv = findViewById(R.id.progress_text)
        categoryTV = findViewById(R.id.tv_category)
        tabRecycler = findViewById(R.id.tab_recycler)
        recycler = findViewById(R.id.recycler)
        saveBtn = findViewById(R.id.cv_btn_save)
        submitBtn = findViewById(R.id.cv_btn_submit)

       val tvAgentProfile: AppCompatTextView=findViewById(R.id.tv_agent_profile)
        val tvAgentLocation: AppCompatTextView=findViewById(R.id.tv_agent_location)
        val tvAgentName: AppCompatTextView=findViewById(R.id.tv_agent_name)

        tvAgentName.text = selectedPOI!!.agentName
        tvAgentLocation.text = selectedPOI!!.agentLocation +" Data Collector"

        if (selectedPOI!!.agentName.isNullOrBlank()) {
            tvAgentProfile.text = ""

        }else {

            val parts = selectedPOI!!.agentName.trim().split(" ")

            val initials = parts
                .filter { it.isNotBlank() }
                .map { it.first().uppercaseChar() }
                .take(2)     // only first 2 characters (J + D)
                .joinToString("")

            tvAgentProfile.text  = initials
        }

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
            onRequestLocation =  { latId, lngId ->
                requestLocationPermission(latId, lngId)
            },
            onFieldChanged = { id, value -> vm.updateValue(id, value) },
            onRemovePhoto = { fieldId, uri -> vm.removePhotoUri(fieldId, uri) },
            onAddNotification = onAdd@{
                val errors = vm.addNotification()

                if (errors.isNotEmpty()) {
                    Toast.makeText(this, errors.values.first(), Toast.LENGTH_LONG).show()
                    return@onAdd
                }

                Toast.makeText(this, "Notification added", Toast.LENGTH_SHORT).show()
            },onRemoveNotification = { index ->
                vm.removeNotification(index)
                Toast.makeText(this, "Notification removed", Toast.LENGTH_SHORT).show()
            }
        )
        adapter.setHasStableIds(true)
        recycler.adapter = adapter

        // Observe schema
        vm.schemaLive.observe(this) { schema ->
            schema?.let {
                titleTv.text = selectedPOI!!.poiName
                progressTv.text = "${selectedPOI!!.progress ?: 0}%"
                categoryTV.text = it.tags[0]
                tabAdapter.submitTabs(it.tabs)
               // it.tabs.firstOrNull()?.let { tab -> showTab(tab.id) }
            }
        }

//        vm.fieldChangeLive.observe(this) { fieldId ->
//            if (lastShownTabId == null) return@observe
//            val pos = adapterPositionForField(fieldId)
//            if (pos >= 0) adapter.notifyItemChanged(pos)
//        }
        vm.fieldChangeLive.observe(this) { fieldId ->
           // adapter.updateValue(fieldId, vm.valuesLive.value?.get(fieldId))
            refreshCurrentTab()
        }
//        vm.fieldChangeLive.observe(this) {
//            refreshCurrentTab()
//        }

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
            vm.saveDraftToRoom(""+poiId)
            val tabs = schema.tabs
            val idx = tabs.indexOfFirst { it.id == currentTabId }
            if (idx in 0 until tabs.lastIndex) {
                val nextTab = tabs[idx + 1]
                lastShownTabId = nextTab.id
                tabAdapter.highlightTab(nextTab.id)
                showTab(nextTab.id)
//                Toast.makeText(this, "Draft saved. Moving to ${nextTab.title}", Toast.LENGTH_SHORT)
//                    .show()
            } else {
               // val payload = vm.buildPayload()
               // vm.submit(""+poiId)

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
           // val payload = vm.buildPayload()
            vm.submit(""+poiId,selectedPOI)
//            Toast.makeText(this, "Data Saved Successfully...", Toast.LENGTH_SHORT)
//                .show()
//            finish()
        }

        // Load schema from backend mock
//        var s = "";
//        if(intent.getIntExtra("KEY_ID",1)==2){
//           s  = MockNetwork.fetchSchemaString2()
//        }else{
//           s   = MockNetwork.fetchSchemaString()
//        }


        vm.loadSchemaFromString(schema)

        // STEP 2 → Load draft (async) and restore values
//        vm.loadDraft(""+poiId) {
//
//            // STEP 3 → After draft loaded, now build UI
//            val loadedSchema = vm.schemaLive.value ?: return@loadDraft
//
//            titleTv.text = loadedSchema.title
//            tabAdapter.submitTabs(loadedSchema.tabs)
//
//            // Open first tab
//            loadedSchema.tabs.firstOrNull()?.let { showTab(it.id) }
//            if (schema != null) {
//                adapter.submitFields(schema.tabs.first().fields, vm.valuesLive.value ?: emptyMap())
//            }
//        }
        vm.loadDraft("" + poiId) {
            val loadedSchema = vm.schemaLive.value ?: return@loadDraft
            val firstTab = loadedSchema.tabs.first()
            showTab(firstTab.id)   // spinner restores correctly
        }



        setObserver()
    }

    private fun setObserver() {


        vm.submitPOIResponse.observe(
            this,
            androidx.lifecycle.Observer { state ->
                if (state == null) {
                    return@Observer
                }

                when (state) {

                    is ResponseHandler.Loading -> {
                        Utils.showProgressDialog(this)
                    }
                    is ResponseHandler.OnFailed -> {
                        Utils.hideProgressDialog()

                        if (state.code == 401) {
                            //  Utils.showUnAuthAlertDialog(this)
                        } else {
                            state.message.let { m ->
                                DebugLog.e("Config Api Error Response : $m")


                                CustomDialogBuilder(this)
                                    .setTitle("Error")
                                    .setMessage(m
                                    )
                                    .setPositiveButton("OK") {


                                    }
                            }
                                .setCancelable(false)
                                .show()
                        }
                    }


                    is ResponseHandler.OnSuccessResponse<ResponseData<Int>?> -> {

                        state.response?.let {
                            Utils.hideProgressDialog()
                            CustomDialogBuilder(this)
                                .setTitle("Successful")
                                .setMessage("Data Submitted Successfully"
                                )
                                .setPositiveButton("OK") {


                                    vm.deleteDraftAfterSubmit(""+poiId) {
                                        setResult(Activity.RESULT_OK)
                                        finish()  // close after draft is removed
                                    }
                                }

                            .setCancelable(false)
                            .show()

                        }


                        Utils.hideProgressDialog()

                    }
                }
            })

        vm.locationFetchState.observe(this, Observer{
            if(it){


            }else{
                Toast.makeText(this@DynamicFormActivity,"Please try again...", Toast.LENGTH_LONG).show()
            }
        })


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
    private fun requestLocationPermission(latId: String, lngId: String) {
        pendingLatField = latId
        pendingLngField = lngId

        checkLocationPermissionAndStart()


    }

    private fun refreshCurrentTab() {
        val schema = vm.schemaLive.value ?: return
        val tabId = lastShownTabId ?: return
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return

        adapter.submitFields(tab.fields, vm.valuesLive.value ?: emptyMap())
    }

    private fun checkLocationPermissionAndStart() {

        if (permissionUtils.isLocationPermissionGranted(this)) {
            checkGps()
        } else {
            permissionUtils.request(requiredPermissions) { granted ->
                if (granted)   // Permission OK -> check GPS then start location fetch
                    checkGpsThenStartLocationFetch()
            }
        }
    }

    private fun checkGpsThenStartLocationFetch() {
        if (!permissionUtils.isGpsEnabled(this)) {
            showEnableGpsDialog()
        } else {
            fetchLocation()
                  }
    }


    private fun checkGps() {
        if (!permissionUtils.isGpsEnabled(this)) {
            showEnableGpsDialog()
        } else {
            fetchLocation()
        }
    }



    private fun showEnableGpsDialog() {
        permissionUtils.request(requiredPermissions) { granted ->
            if (granted) {
                // Now check if location is turned ON
                permissionUtils.checkAndEnableLocationSettings { enabled ->
                    if (enabled) {
                        //  Start fetching location
                      fetchLocation()
                    } else {

                    }
                }
            }
        }
    }

    private fun fetchLocation(){
//        Toast.makeText(this@DynamicFormActivity,"Fetching your location, please wait…", Toast.LENGTH_LONG).show()
//        vm.fetchAccurateLocation(pendingLatField, pendingLngField)
        mapLauncher.launch(Intent(this, MapPickerActivity::class.java))
    }

    private val mapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val lat = result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
                val lng = result.data?.getDoubleExtra("lng", 0.0) ?: 0.0

                vm.updateValue("latitude", lat)
                vm.updateValue("longitude", lng)

                vm.notifyFieldChanged("latitude")
                vm.notifyFieldChanged("longitude")
            }
        }




}
