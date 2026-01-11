package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.core.base.BaseActivity
import com.core.customviews.CustomDialogBuilder
import com.core.utils.DebugLog
import com.core.utils.PermissionUtils
import com.core.utils.Utils
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.movodream.localguru.R
import com.movodream.localguru.data_collection.model.FormSchema
import com.movodream.localguru.data_collection.model.PhotoWithMeta
import com.movodream.localguru.data_collection.model.TaskItem
import com.movodream.localguru.data_collection.presentation.FormViewModel
import com.movodream.localguru.data_collection.ui.adapter.DynamicFormAdapter
import com.movodream.localguru.data_collection.ui.adapter.TabAdapter
import com.network.client.ResponseHandler
import com.network.model.BulkSubPoiItem
import com.network.model.ResponseData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.collections.emptyList
import kotlin.collections.indexOfFirst
import kotlin.text.contains
import kotlin.text.substringBefore


class DynamicFormActivity : BaseActivity() {

    private lateinit var removeUri: String
    private lateinit var removePhotoFieldId: String
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
//    private val pickImages =
//        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
//            if (uris.isNotEmpty() && currentPhotoFieldId != null) {
//                vm.addPhotoUris(currentPhotoFieldId!!, uris)
//            }
//        }
    //Temp change
    private var currentSubPoiIndex = 0
    private var totalSubPoiCount = 0
    // Holds pending facility location before save
    private var facilityLat: String? = null
    private var addressLat: String? = null
    private var facilityLng: String? = null
    private var addressLng: String? = null


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

        vm._isRevisionState.value = intent.getBooleanExtra("KEY_IS_REVISION",false)
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
            onRemovePhoto = { fieldId, uri ->

                val uriStr = uri.toString()

                if (uriStr.contains("|")) {
                    //  Server photo
                    removePhotoFieldId = fieldId
                    removeUri = uriStr
                    callDeletePhotoAPI()
                } else {
                    //  Local photo
                    vm.removePhoto(fieldId, uriStr)
                }
            },

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
            }, onPreviewPhotos = { fieldId, uris ->
               // showPhotoPreviewDialog(fieldId, uris)  // final call
                val intent = Intent(this@DynamicFormActivity, PhotoMetadataActivity::class.java).apply {
                    putExtra("fieldId", fieldId)
                    putParcelableArrayListExtra(
                        "photos",
                        ArrayList(vm.getPhotoMetadata(fieldId))
                    )
                }
                photoMetaLauncher.launch(intent)
            },onAddSubPoi = {
                val errors = vm.addSubPoi()
                if (errors.isNotEmpty())
                    Toast.makeText(this, errors.values.first(), Toast.LENGTH_LONG).show()
            },onRemoveSubPoi = { index ->
                vm.removeSubPoi(index)
            },

            onOperationalHoursChanged =  { days, clickedDay ->
                showOperationalHoursDialog(
                    week = days.toMutableList(),
                    editDay = clickedDay
                )
            },
            onAddHoliday = {
                showAddHolidayDialog()
            },
            onAddSeasonal = {
                showAddSeasonalDialog()
            },

            onRemoveHoliday = { index ->
                vm.removeHoliday(index)
            },
            onRemoveSeason = { index ->
                vm.removeSeasonal(index)
            },onRequestRebind = { fieldId ->
                vm.notifyFieldChanged(fieldId) // ✅ ONLY PLACE this exists
            }, onAddEvent = {
                showAddEventDialog()
            }, onRemoveEvent = {event ->
                vm.removeEvent(event)
            }, onAddFacility = {fieldId, title->
                showAddFacilityDialog(fieldId,title)
            }, onRemoveFacility = {fieldId, item ->
               vm.removeFacility(fieldId,item)
            }, onAddAddress = {fieldId, title->
                showAddAddressDialog(fieldId,title)
            }, onRemoveAddress = {fieldId, item ->
                vm.removeAddress(fieldId,item)
            }






            )
        adapter.setHasStableIds(true)
        recycler.adapter = adapter





        // Observe schema
        vm.schemaLive.observe(this) { schema ->
            schema?.let {
                titleTv.text = selectedPOI!!.poiName
              //  progressTv.text = "${selectedPOI!!.progress ?: 0}%"
                categoryTV.text = it.tags[0]
                tabAdapter.submitTabs(it.tabs)
            }
        }

        vm.fieldChangeLive.observe(this) { fieldId ->
            refreshCurrentTab()
        }


        // Save draft + move to next tab
        saveBtn.setOnClickListener {
            val schema = vm.schemaLive.value ?: return@setOnClickListener
            val currentTabId = lastShownTabId ?: return@setOnClickListener

            if (currentTabId != "add_sub_poi_of_poi") {

                val errors = vm.validateTab(currentTabId)
                if (errors.isNotEmpty()) {
                    Toast.makeText(this, errors.values.first(), Toast.LENGTH_LONG).show()
                    adapter.setErrors(errors)
                    return@setOnClickListener
                }
            }

            // ✅ Validate ONLY addSubPois list if required
            if (currentTabId == "add_sub_poi_of_poi") {
                val subPoiErrors = vm.validateSubPoiListForDraft(currentTabId)
                if (subPoiErrors.isNotEmpty()) {
                    Toast.makeText(this, subPoiErrors.values.first(), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
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
            } else {


            }
        }

        // Submit entire form
        submitBtn.setOnClickListener {
            val schema = vm.schemaLive.value ?: return@setOnClickListener
            val allErrors = mutableMapOf<String, String>()
//            schema.tabs.forEach { t -> allErrors.putAll(vm.validateTab(t.id)) }
            schema.tabs.forEach { tab ->

                //  Skip normal validation for Sub-POI tab
                if (tab.id != "add_sub_poi_of_poi") {
                    allErrors.putAll(vm.validateTab(tab.id))
                } else {
                    //  Validate ONLY sub-poi list if required
                    allErrors.putAll(vm.validateSubPoiListForDraft(tab.id))
                }
            }
            // Custom metadata validation
            val metaField = schema.tabs
                .flatMap { it.fields }
                .firstOrNull { it.id == "customMetadata" }

            allErrors.putAll(
                vm.validateCustomMetadata(metaField?.required == true)
            )

            if (allErrors.isNotEmpty()) {
                Toast.makeText(this, allErrors.values.first(), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            vm.submit(""+poiId,selectedPOI)

        }


        vm.loadSchemaFromString(schema)

        vm.loadDraft("" + poiId) {

            //  Auto-fill siteName from POI list into form
            val poiName = selectedPOI?.poiName?.trim().orEmpty()
            val agentId = selectedPOI?.agentId?.trim().orEmpty()

            if (poiName.isNotBlank()) {
                vm.updateValue("siteName", poiName)     // set value in state
                vm.notifyFieldChanged("siteName")       // refresh adapter
            }
            if (agentId.isNotBlank()) {
                vm.updateValue("collectorId", agentId)     // set value in state
                vm.notifyFieldChanged("collectorId")       // refresh adapter
            }
            val loadedSchema = vm.schemaLive.value ?: return@loadDraft
            val firstTab = loadedSchema.tabs.first()
            showTab(firstTab.id)   // spinner restores correctly
        }
        vm.progressPercent.observe(this) { percent ->
            progressTv.text = "${percent ?: 0}%"

        }


        setObserver()
    }

    private fun callDeletePhotoAPI() {
        var urlString  = removeUri.toString()
        vm.callDeletePhotoAPI(urlString, selectedPOI!!.agentId ,""+poiId)
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

                            if (vm._isRevisionState.value == false&&vm.hasAtLeastOneSubPoi()) {
                                //  vm.submitSubPoi() // or call Sub POI API builder
                                startSubPoiSubmission()
                               // startSubPoiBulkUpload()
                            }else {
                                CustomDialogBuilder(this)
                                    .setTitle("Successful")
                                    .setMessage(
                                        if (vm._isRevisionState.value == true)
                                            "Data Updated Successfully"
                                        else
                                            "Data Submitted Successfully"
                                    )

                                    .setPositiveButton("OK") {


                                        vm.deleteDraftAfterSubmit("" + poiId) {
                                            setResult(Activity.RESULT_OK)
                                            finish()  // close after draft is removed
                                        }
                                    }

                                    .setCancelable(false)
                                    .show()
                            }

                        }


                        //Utils.hideProgressDialog()

                    }
                }
            })



        vm.bulkSubPOIResponse.observe(
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
                            CustomDialogBuilder(this)
                                .setTitle("Error")
                                .setMessage("Error : 401"
                                )
                                .setPositiveButton("Retry") {

                                    // startSubPoiBulkUpload()
                                    submitCurrentSubPoi()
                                }

                            .setCancelable(false)
                            .show()
                        } else {
                            state.message.let { m ->
                                DebugLog.e("Config Api Error Response : $m")


                                CustomDialogBuilder(this)
                                    .setTitle("Error")
                                    .setMessage(m
                                    )
                                    .setPositiveButton("Retry") {

                                       // startSubPoiBulkUpload()
                                        submitCurrentSubPoi()
                                    }
                            }
                                .setCancelable(false)
                                .show()
                        }
                    }


                    is ResponseHandler.OnSuccessResponse<List<BulkSubPoiItem>?> -> {

                        Utils.hideProgressDialog()
                        state.response?.let {


                            //  MOVE TO NEXT
                            currentSubPoiIndex++

                            if (currentSubPoiIndex < totalSubPoiCount) {

                                // 👉 Submit next Sub-POI silently
                                submitCurrentSubPoi()

                            } else {

                                //  ALL SUB-POIs DONE (SHOW ONCE)
                                CustomDialogBuilder(this)
                                    .setTitle("Successful")
                                    .setMessage(
                                        "Data submitted successfully! You can view the details of the selected POI in the Completed tab of the Tasks section."
                                    )
                                    .setPositiveButton("OK") {
                                        vm.deleteDraftAfterSubmit("" + poiId) {
                                            setResult(Activity.RESULT_OK)
                                            finish()
                                        }
                                    }
                                    .setCancelable(false)
                                    .show()
                            }

                        }




                    }
                }
            })

        vm.deleteGalleryPhoto.observe(
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
                          //  vm.removePhotoUri(removePhotoFieldId,removeUri)

                        }




                        Utils.hideProgressDialog()

                    }
                }
            })




    }

    private fun showTab(tabId: String) {
        lastShownTabId = tabId
        val schema = vm.schemaLive.value ?: return
        val tab = schema.tabs.firstOrNull { it.id == tabId } ?: return
        //  Ensure only when operational hours tab opens
        if (tab.id == "operational_hours") {
            vm.ensureOperationalHours()
            vm.ensureHolidayAndSeasonal()
        }
        adapter.submitFields(tab.fields, vm.valuesLive.value ?: emptyMap())
        val pos = schema.tabs.indexOf(tab)
        if (pos >= 0) tabRecycler.smoothScrollToPosition(pos)
    }

    /**
     * Request correct gallery permission for all Android versions.
     */
    private fun requestGalleryAndPick(fieldId: String) {
        currentPhotoFieldId = fieldId

        //  Android 13 (API 33) and above: no need for manual permission with GetMultipleContents()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickImages.launch("image/*")
            return
        }

        // For Android 12 and below, we still need READ_EXTERNAL_STORAGE
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
        if(!pendingLatField.startsWith("subPoi")){
            vm.fetchAccurateLocation()
        }
        mapLauncher.launch(Intent(this, MapPickerActivity::class.java))
    }

    private val mapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val lat = result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("lng", 0.0) ?: 0.0

            val rawLocation = result.data?.getStringExtra("locationInfo").orEmpty().trim()
            val physicalAddress = result.data?.getStringExtra("address").orEmpty().trim()

            val parts = rawLocation.split("|").map { it.trim() }

            val city = parts.getOrNull(0).orEmpty()
            val state = parts.getOrNull(1).orEmpty()
            val country = parts.getOrNull(2).orEmpty()

            // ✅ Update requested latitude / longitude
            vm.updateValue(pendingLatField, lat)
            vm.updateValue(pendingLngField, lng)

            // ✅ Decide target address fields dynamically
            val isSubPoi = pendingLatField.startsWith("subPoi")

            if (isSubPoi) {
                vm.updateValue("subPoiPhysicalAddress", physicalAddress)
            } else {
                vm.updateValue("physicalAddress", physicalAddress)
                vm.updateValue("localityTown", city)
                vm.updateValue("regionState", state)
                vm.updateValue("country", country)
            }

            vm.updateValue("pinVerifiedViaGps", "Y")

            // 🔄 Notify UI
            vm.notifyFieldChanged(pendingLatField)
            vm.notifyFieldChanged(pendingLngField)

            if (isSubPoi) {
                vm.notifyFieldChanged("subPoiPhysicalAddress")
            } else {
                vm.notifyFieldChanged("physicalAddress")
                vm.notifyFieldChanged("localityTown")
                vm.notifyFieldChanged("regionState")
                vm.notifyFieldChanged("country")
            }
        }


    private fun showPhotoPreviewDialog(fieldId: String, uris: MutableList<Uri>)
    {
        // Always work on a mutable reference to actual list
        val list = uris.toMutableList()

        if (list.isEmpty()) return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_photo_preview)

        val zoomImg = dialog.findViewById<AppCompatImageView>(R.id.zoomImg)
        val tvFile = dialog.findViewById<AppCompatTextView>(R.id.tvFileName)
        val tvCount = dialog.findViewById<AppCompatTextView>(R.id.tvCount)

        val btnPrev = dialog.findViewById<AppCompatImageButton>(R.id.btnPrev)
        val btnNext = dialog.findViewById<AppCompatImageButton>(R.id.btnNext)
        val btnDelete = dialog.findViewById<AppCompatImageButton>(R.id.btnDelete)

        var index = 0

        fun refreshUI() {
            if (list.isEmpty()) { dialog.dismiss(); return }

            val raw = list[index].toString()

// 🔥 Extract pure URL if stored as url|id
            val url = if (raw.contains("|")) raw.substringBefore("|") else raw

            val model: Any = when {
                url.startsWith("content://") -> Uri.parse(url)
                url.startsWith("file://") -> File(Uri.parse(url).path!!)
                url.startsWith("http") -> url // remote stored url
                else -> File(url) // fallback
            }

            Glide.with(zoomImg.context)
                .load(model)
                //.placeholder(R.drawable.ic_cloud_upload)
                .into(zoomImg)

            val fileName = url.substringAfterLast('/').substringBefore('?')
            tvFile.text = fileName

            tvCount.text = "${index + 1}/${list.size}"

            btnPrev.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
            btnNext.visibility = if (index == list.lastIndex) View.INVISIBLE else View.VISIBLE
        }


        refreshUI()

        btnPrev.setOnClickListener {
            if (index > 0) index--
            refreshUI()
        }

        btnNext.setOnClickListener {
            if (index < list.lastIndex) index++
            refreshUI()
        }

        btnDelete.setOnClickListener {
            val removedUri = uris[index]

            // Remove from ViewModel source
            vm.removePhotoUri(fieldId, removedUri)

            // Remove from dialog list
            uris.removeAt(index)   // Now works ✔

            vm.notifyFieldChanged(fieldId)

            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show()

            dialog.dismiss()
        }

        dialog.show()
    }



    // Holds a safe template copy of slots for applying to multiple days
    private fun templateSlotsCopy(
        slots: List<FormViewModel.TimeSlot>
    ): MutableList<FormViewModel.TimeSlot> {
        return slots.map {
            FormViewModel.TimeSlot(
                open = it.open,
                close = it.close
            )
        }.toMutableList()
    }

    private fun showOperationalHoursDialog(
        week: MutableList<FormViewModel.OperationalDay>,
        editDay: FormViewModel.WeekDay
    ) {
        // --- defensive copy ---
        val workingCopy = week.map {
            it.copy(slots = it.slots.toMutableList())
        }.toMutableList()
        val sourceDay = workingCopy.first { it.day == editDay }

        // template = what user edits inside dialog
        val template = workingCopy.first { it.day == editDay }
            .copy(slots = templateSlotsCopy(sourceDay.slots)
            )

        val selectedDays = mutableSetOf(editDay)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_operational_hours)

        val chipGroup = dialog.findViewById<FlexboxLayout>(R.id.dayChips)!!
        val rbClosed = dialog.findViewById<RadioButton>(R.id.rbClosed)!!
        val rb24 = dialog.findViewById<RadioButton>(R.id.rb24Hours)!!
        val rbCustom = dialog.findViewById<RadioButton>(R.id.rbCustom)!!
        val slotContainer = dialog.findViewById<LinearLayout>(R.id.slotContainer)!!
        val btnAddSlot = dialog.findViewById<TextView>(R.id.btnAddSlot)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        // --- day chips ---
        FormViewModel.WeekDay.values().forEach { day ->
            val chip = createDayChip(day, selectedDays)

            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 20   // horizontal spacing
                bottomMargin = 2 // vertical spacing (if wrapping to next line)
            }

            chip.layoutParams = params

            // ✅ Pre-highlight edited day
            if (selectedDays.contains(day)) {
                chip.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_chip_selected)
            }

            chipGroup.addView(chip)
        }

        // --- prefill mode ---
        when {
            template.isClosed -> rbClosed.isChecked = true
            template.isOpen24Hours -> rb24.isChecked = true
            else -> rbCustom.isChecked = true
        }

        fun refreshSlotsUI() {
            slotContainer.removeAllViews()
            template.slots.forEach { slot ->
                addSlotRow(this, slotContainer, template, slot)
            }
        }

        rbClosed.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                template.isClosed = true
                template.isOpen24Hours = false
                template.slots.clear()
                slotContainer.removeAllViews()
            }
        }

        rb24.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                template.isClosed = false
                template.isOpen24Hours = true
                template.slots.clear()
                slotContainer.removeAllViews()
            }
        }

        rbCustom.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                template.isClosed = false
                template.isOpen24Hours = false
                if (template.slots.isEmpty()) {
                    template.slots.add(FormViewModel.TimeSlot(0, 0))
                }
                refreshSlotsUI()
            }
        }

        btnAddSlot.setOnClickListener {
            template.isClosed = false
            template.isOpen24Hours = false
            rbCustom.isChecked = true
            val slot = FormViewModel.TimeSlot(0, 0)
            template.slots.add(slot)
            addSlotRow(this, slotContainer, template, slot)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val error = validateSlots(template)
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            selectedDays.forEach { day ->
                val d = workingCopy.first { it.day == day }

                when {
                    rbClosed.isChecked -> {
                        d.isClosed = true
                        d.isOpen24Hours = false
                        d.slots.clear()
                    }

                    rb24.isChecked -> {
                        d.isClosed = false
                        d.isOpen24Hours = true
                        d.slots.clear()
                    }

                    else -> {
                        d.isClosed = false
                        d.isOpen24Hours = false
                        d.slots.clear()
                        d.slots.addAll(templateSlotsCopy(template.slots)) // ✅ CORRECT
                    }
                }
            }


            vm.updateValue("operationalHours", workingCopy)
            vm.notifyFieldChanged("operationalHours")
            dialog.dismiss()
        }

        if (!template.isClosed && !template.isOpen24Hours) {
            refreshSlotsUI()
        }

        dialog.show()
    }


    /**
     * Validates time slots for a single day.
     * @return null if valid, otherwise error message
     */
    fun validateSlots(day: FormViewModel.OperationalDay): String? {
        if (day.isClosed || day.isOpen24Hours) return null

        if (day.slots.isEmpty()) return "Please add at least one time slot"

        val sorted = day.slots.sortedBy { it.open }

        sorted.forEachIndexed { i, s ->
            if (s.open <= 0 || s.close <= 0) return "Please select both open & close time"
            if (s.open >= s.close) return "Open time must be before close time"
            if (i > 0 && sorted[i - 1].close > s.open)
                return "Time slots must not overlap"
        }
        return null
    }



    private fun createDayChip(
        day: FormViewModel.WeekDay,
        selected: MutableSet<FormViewModel.WeekDay>
    ): TextView {
        return TextView(this).apply {
            text = day.name.first().toString()
            setPadding(24, 12, 24, 12)
            background = ContextCompat.getDrawable(context, R.drawable.bg_chip)
            typeface = ResourcesCompat.getFont(context, com.core.R.font.dm_sans_bold)
            setTextColor(Color.BLACK)
            setOnClickListener {
                if (selected.contains(day)) {
                    selected.remove(day)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_chip)
                } else {
                    selected.add(day)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_chip_selected)
                }
            }
        }
    }


    private fun addSlotRow(
        context: Context,
        container: LinearLayout,
        day: FormViewModel.OperationalDay,
        slot: FormViewModel.TimeSlot
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val openTv = TextView(context).apply {
            text = if (slot.open > 0) fromMinutes(slot.open) else "Open"
            setPadding(24, 16, 24, 16)
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_bordered)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val dash = TextView(context).apply {
            text = " – "
            setPadding(8, 0, 8, 0)
        }

        val closeTv = TextView(context).apply {
            text = if (slot.close > 0) fromMinutes(slot.close) else "Close"
            setPadding(24, 16, 24, 16)
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_bordered)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val delete = ImageView(context).apply {
            setImageResource(R.drawable.ic_delete)
            setPadding(16, 16, 16, 16)
        }

        openTv.setOnClickListener {
            showTimePicker(context) { display, minutes ->
                slot.open = minutes
                openTv.text = display
            }
        }

        closeTv.setOnClickListener {
            showTimePicker(context) { display, minutes ->
                slot.close = minutes
                closeTv.text = display
            }
        }

        delete.setOnClickListener {
            day.slots.remove(slot)
            container.removeView(row)
        }

        row.addView(openTv)
        row.addView(dash)
        row.addView(closeTv)
        row.addView(delete)
        container.addView(row)
    }



    private fun collectSlots(container: LinearLayout): List<FormViewModel.TimeSlot> {
        val list = mutableListOf<FormViewModel.TimeSlot>()

        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            val open = v.findViewById<TextView>(R.id.tvOpen).text.toString()
            val close = v.findViewById<TextView>(R.id.tvClose).text.toString()

            list.add(
                FormViewModel.TimeSlot(
                    open = toMinutes(open),
                    close = toMinutes(close)
                )
            )
        }
        return list
    }



    fun showTimePicker(
        context: Context,
        onResult: (display: String, minutes: Int) -> Unit
    ) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour24 = picker.hour
            val minute = picker.minute

            val minutesFromMidnight = hour24 * 60 + minute

            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            val ampm = if (hour24 < 12) "AM" else "PM"

            val display = String.format(
                Locale.US,
                "%d:%02d %s",
                hour12,
                minute,
                ampm
            )

            onResult(display, minutesFromMidnight)
        }

        picker.show((context as AppCompatActivity).supportFragmentManager, "TIME_PICKER")
    }


    fun toMinutes(time: String): Int {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        sdf.isLenient = false

        val date = sdf.parse(time)
            ?: throw IllegalArgumentException("Invalid time: $time")

        val cal = Calendar.getInstance()
        cal.time = date

        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    fun fromMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)

        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(cal.time)
    }


    private fun fmt(min: Int): String {
        val h24 = min / 60
        val m = min % 60

        val ampm = if (h24 < 12) "am" else "pm"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }

        return "$h12:${m.toString().padStart(2, '0')} $ampm"
    }


    private fun showAddHolidayDialog() {

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_add_holiday)
        val etName = dialog.findViewById<EditText>(R.id.etName)!!
        val tvDate = dialog.findViewById<TextView>(R.id.tvDate)!!
        val rbClosed = dialog.findViewById<RadioButton>(R.id.rbClosed)!!
        val rb24 = dialog.findViewById<RadioButton>(R.id.rb24Hours)!!
        val rbCustom = dialog.findViewById<RadioButton>(R.id.rbCustom)!!
        val slotContainer = dialog.findViewById<LinearLayout>(R.id.slotContainer)!!
        val btnAddSlot = dialog.findViewById<TextView>(R.id.btnAddSlot)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        var selectedDate = ""

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                    tvDate.text = selectedDate
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val slots = mutableListOf<FormViewModel.TimeSlot>()

        btnAddSlot.setOnClickListener {
            addSlotRow(this, slotContainer, slots)
        }
        rbClosed.setOnCheckedChangeListener { _, checked ->
            if (checked) {

                slots.clear()
                slotContainer.removeAllViews()
            }
        }

        rb24.setOnCheckedChangeListener { _, checked ->
            if (checked) {

                slots.clear()
                slotContainer.removeAllViews()
            }
        }


        rbCustom.setOnCheckedChangeListener { _, checked ->
            if (checked) {


                addSlotRow(this, slotContainer, slots)
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()


            val h = FormViewModel.HolidayException(
                name = name,
                date = selectedDate,
                isClosed = rbClosed.isChecked,
                isOpen24Hours = rb24.isChecked,
                slots = if (rbCustom.isChecked) slots else mutableListOf()
            )
            val existing =
                vm.valuesLive.value?.get("holidayExceptions")
                        as? List<FormViewModel.HolidayException>
                    ?: emptyList()

            val error = validateHoliday(h, existing)

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.addHoliday(h)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddSeasonalDialog() {

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.dialog_add_season)
        val etName = dialog.findViewById<EditText>(R.id.etSeasonName)!!
        val tvStart = dialog.findViewById<TextView>(R.id.tvStartDate)!!
        val tvEnd = dialog.findViewById<TextView>(R.id.tvEndDate)!!
        val rbClosed = dialog.findViewById<RadioButton>(R.id.rbClosed)!!
        val rb24 = dialog.findViewById<RadioButton>(R.id.rb24Hours)!!
        val rbCustom = dialog.findViewById<RadioButton>(R.id.rbCustom)!!
        val slotContainer = dialog.findViewById<LinearLayout>(R.id.slotContainer)!!
        val btnAddSlot = dialog.findViewById<TextView>(R.id.btnAddSlot)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        var startDate = ""
        var endDate = ""

        fun pickDate(cb: (String) -> Unit) {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d -> cb("%04d-%02d-%02d".format(y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        tvStart.setOnClickListener { pickDate { startDate = it; tvStart.text = it } }
        tvEnd.setOnClickListener { pickDate { endDate = it; tvEnd.text = it } }

        val slots = mutableListOf<FormViewModel.TimeSlot>()

        btnAddSlot.setOnClickListener {
            addSlotRow(this, slotContainer, slots)
        }
        rbClosed.setOnCheckedChangeListener { _, checked ->
            if (checked) {

                slots.clear()
                slotContainer.removeAllViews()
            }
        }

        rb24.setOnCheckedChangeListener { _, checked ->
            if (checked) {

                slots.clear()
                slotContainer.removeAllViews()
            }
        }


        rbCustom.setOnCheckedChangeListener { _, checked ->
            if (checked) {


                addSlotRow(this, slotContainer, slots)
            }
        }


        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()


            val s = FormViewModel.SeasonalHours(
                name = name,
                startDate = startDate,
                endDate = endDate,
                isClosed = rbClosed.isChecked,
                isOpen24Hours = rb24.isChecked,
                slots = if (rbCustom.isChecked) slots else mutableListOf()
            )

            val existing =
                vm.valuesLive.value?.get("seasonalHours")
                        as? List<FormViewModel.SeasonalHours>
                    ?: emptyList()

            val error = validateSeasonal(s, existing)

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.addSeasonal(s)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addSlotRow(
        context: Context,
        container: LinearLayout,
        slots: MutableList<FormViewModel.TimeSlot>
    ) {
        val slot = FormViewModel.TimeSlot(open = -1, close = -1)
        slots.add(slot)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val openTv = TextView(context).apply {
            text = "Open"
            setPadding(24, 16, 24, 16)
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_bordered)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val dash = TextView(context).apply {
            text = " – "
            setPadding(8, 0, 8, 0)
        }

        val closeTv = TextView(context).apply {
            text = "Close"
            setPadding(24, 16, 24, 16)
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_bordered)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val delete = ImageView(context).apply {
            setImageResource(R.drawable.ic_delete)
            setPadding(16, 16, 16, 16)
        }

        openTv.setOnClickListener {
            showTimePicker { display, minutes ->
                openTv.text = display
                slot.open = minutes
            }
        }

        closeTv.setOnClickListener {
            showTimePicker { display, minutes ->
                closeTv.text = display
                slot.close = minutes
            }
        }

        delete.setOnClickListener {
            slots.remove(slot)
            container.removeView(row)
        }

        row.addView(openTv)
        row.addView(dash)
        row.addView(closeTv)
        row.addView(delete)

        container.addView(row)
    }
    private fun showTimePicker(
        onResult: (display: String, minutes: Int) -> Unit
    ) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .build()

        picker.addOnPositiveButtonClickListener {
            val h24 = picker.hour
            val m = picker.minute

            val minutes = h24 * 60 + m

            val h12 = when {
                h24 == 0 -> 12
                h24 > 12 -> h24 - 12
                else -> h24
            }
            val ampm = if (h24 < 12) "AM" else "PM"

            onResult(
                String.format("%d:%02d %s", h12, m, ampm),
                minutes
            )
        }

        picker.show(supportFragmentManager, "TIME_PICKER")
    }
    fun validateHoliday(
        newHoliday: FormViewModel.HolidayException,
        existing: List<FormViewModel.HolidayException>
    ): String? {

        if (newHoliday.name.isBlank()) {
            return "Please enter holiday name"
        }

        if (newHoliday.date.isBlank()) {
            return "Please select holiday date"
        }

        // duplicate date
        if (existing.any { it.date == newHoliday.date }) {
            return "Holiday already exists for this date"
        }

        // mode validation
        if (newHoliday.isClosed || newHoliday.isOpen24Hours) {
            if (newHoliday.slots.isNotEmpty()) {
                return "Custom hours not allowed for Closed / 24 hours"
            }
            return null
        }

        // custom slots
        return validateSlots(newHoliday.slots)
    }

    fun validateSlots(slots: List<FormViewModel.TimeSlot>): String? {

        if (slots.isEmpty()) {
            return "Please add at least one time slot"
        }

        // open < close
        slots.forEachIndexed { index, s ->
            if (s.open >= s.close) {
                return "Invalid time in slot ${index + 1} (Open must be before Close)"
            }
        }

        // overlap check
        val sorted = slots.sortedBy { it.open }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].close > sorted[i + 1].open) {
                return "Time slots must not overlap"
            }
        }

        return null
    }


    fun validateSeasonal(
        newSeason: FormViewModel.SeasonalHours,
        existing: List<FormViewModel.SeasonalHours>
    ): String? {

        if (newSeason.name.isBlank()) {
            return "Please enter season name"
        }

        if (newSeason.startDate.isBlank() || newSeason.endDate.isBlank()) {
            return "Please select start & end date"
        }

        val start = parseDate(newSeason.startDate)
        val end = parseDate(newSeason.endDate)

        if (start > end) {
            return "Start date must be before end date"
        }

        // overlap check
        existing.forEach { s ->
            val sStart = parseDate(s.startDate)
            val sEnd = parseDate(s.endDate)

            if (start <= sEnd && end >= sStart) {
                return "Season overlaps with existing season (${s.name})"
            }
        }

        // mode validation
        if (newSeason.isClosed || newSeason.isOpen24Hours) {
            if (newSeason.slots.isNotEmpty()) {
                return "Custom hours not allowed for Closed / 24 hours"
            }
            return null
        }

        return validateSlots(newSeason.slots)
    }
    private fun parseDate(date: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.isLenient = false
        return sdf.parse(date)?.time
            ?: throw IllegalArgumentException("Invalid date: $date")
    }


    private fun startSubPoiBulkUpload() {
        vm.submitBulkSubPOIAfterMainPOI()
    }


    private fun startSubPoiSubmission() {
        val list = vm.valuesLive.value?.get("addSubPois") as? List<*>
            ?: emptyList<Map<String, Any?>>()

        if (list.isEmpty()) return

        totalSubPoiCount = list.size
        currentSubPoiIndex = 0

        submitCurrentSubPoi()
    }

    private fun submitCurrentSubPoi() {
        //  Tell ViewModel which Sub-POI to submit
        vm.currentSubPoiIndex = currentSubPoiIndex

        vm.submitBulkSubPOIAfterMainPOI()
    }





    // -------------------------------
    // Gallery picker
    // -------------------------------
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->

            if (uris.isEmpty() || currentPhotoFieldId == null) return@registerForActivityResult

            val fieldId = currentPhotoFieldId!!
            val appContext = applicationContext

            // ✅ Existing metadata (already saved)
            val existing = vm.getPhotoMetadata(fieldId).toMutableList()

            // ✅ Existing file URIs (avoid duplicates)
            val existingUriSet = existing.map { it.uri }.toSet()

            // ✅ Convert picked content:// → file://
            uris.forEach { pickedUri ->
                val fileUri = copyUriToInternalFile(appContext, pickedUri)
                    ?: return@forEach

                val fileUriStr = fileUri.toString()

                if (!existingUriSet.contains(fileUriStr)) {
                    existing.add(
                        PhotoWithMeta(
                            uri = fileUriStr,     // ✅ ALWAYS file://
                            label = "",
                            description = "",
                            latitude = 0.0,
                            longitude = 0.0
                        )
                    )
                }
            }

            // ✅ Open metadata screen with MERGED list
            val intent = Intent(this, PhotoMetadataActivity::class.java).apply {
                putExtra("fieldId", fieldId)
                putParcelableArrayListExtra("photos", ArrayList(existing))
            }

            photoMetaLauncher.launch(intent)
        }





    private fun copyUriToInternalFile(context: Context, uri: Uri): Uri? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(
                context.filesDir,
                "photo_${System.currentTimeMillis()}.jpg"
            )

            input.use { inputStream ->
                file.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
            }

            Uri.fromFile(file) // ✅ file:// URI
        } catch (e: Exception) {
            null
        }
    }



    private val photoMetaLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val data = result.data ?: return@registerForActivityResult
            val fieldId = data.getStringExtra("fieldId") ?: return@registerForActivityResult

            val updatedList =
                data.getParcelableArrayListExtra<PhotoWithMeta>("result") ?: return@registerForActivityResult

            //  Save FULL merged metadata
            vm.savePhotoMetadata(fieldId, updatedList)

            //  Update form only with URIs (adapter expects this)
            val uriStrings = updatedList.map { it.uri }

            vm.updateValue(fieldId, uriStrings)
            vm.notifyFieldChanged(fieldId)
            vm.calculateProgress()
        }


    private fun showAddEventDialog() {
        val dialog = BottomSheetDialog(
            this,
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )

        dialog.setContentView(R.layout.dialog_add_event)

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        dialog.show()

        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isHideable = false
        }



        val etName = dialog.findViewById<EditText>(R.id.etEventName)!!
        val etDesc = dialog.findViewById<EditText>(R.id.etEventDescription)!!
        val tvDate = dialog.findViewById<TextView>(R.id.tvEventDate)!!
        val slotContainer = dialog.findViewById<LinearLayout>(R.id.slotContainer)!!
        val btnAddSlot = dialog.findViewById<TextView>(R.id.btnAddSlot)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        var date = ""
        val slots = mutableListOf<FormViewModel.TimeSlot>()

        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date = "%04d-%02d-%02d".format(y, m + 1, d)
                    tvDate.text = date
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnAddSlot.setOnClickListener {
            addSlotRow(this, slotContainer, slots)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {

            val name = etName.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (name.isBlank()) {
                Toast.makeText(this,"Please enter event name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (desc.isBlank()) {
                Toast.makeText(this,"Please enter event description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (date.isBlank()) {
                Toast.makeText(this,"Please select event date", Toast.LENGTH_SHORT).show()

                return@setOnClickListener
            }

            val slotError = validateSlots(slots)
            if (slotError != null) {
                Toast.makeText(this,slotError, Toast.LENGTH_SHORT).show()
               return@setOnClickListener
            }

            vm.addEvent(
                FormViewModel.Event(
                    name = name,
                    eventDescription = desc,   // ✅ NEW
                    date = date,
                    slots = slots
                )
            )

            dialog.dismiss()
        }



    }
    private var activePickLocationView: TextView? = null
    private fun showAddFacilityDialog(fieldId: String,title : String) {

        val dialog = BottomSheetDialog(
            this,
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )

        dialog.setContentView(R.layout.dialog_add_facility)

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        dialog.show()

        dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isHideable = false
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)!!
        val etLabel = dialog.findViewById<EditText>(R.id.etLabel)!!
        val etDesc = dialog.findViewById<EditText>(R.id.etDesc)!!
        val tvPickLocation = dialog.findViewById<TextView>(R.id.tvPickLocation)!!
        val etLandmark = dialog.findViewById<EditText>(R.id.etLandmark)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        tvTitle.text = title

        // reset global values
        facilityLat = null
        facilityLng = null
        activePickLocationView = tvPickLocation

        tvPickLocation.setOnClickListener {
            facilityMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
            )
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {

            val label = etLabel.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val landmark = etLandmark.text.toString().trim()

            when {

                label.isBlank() ->  Toast.makeText(this@DynamicFormActivity,"Please enter label", Toast.LENGTH_SHORT).show()

                desc.isBlank() ->   Toast.makeText(this@DynamicFormActivity,"Please enter description", Toast.LENGTH_SHORT).show()

                facilityLat.isNullOrBlank() || facilityLng.isNullOrBlank() ->
                    Toast.makeText(this@DynamicFormActivity,"Please pick location", Toast.LENGTH_SHORT).show()

                else -> {

                    val facility = FormViewModel.FacilityPoint(
                        label = label,
                        description = desc,
                        latitude = facilityLat!!,
                        longitude = facilityLng!!,
                        landmark = landmark
                    )

                    vm.addFacility(fieldId, facility)
                    dialog.dismiss()
                }
            }
        }
    }




    private val facilityMapLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == RESULT_OK && result.data != null) {


            facilityLat = String.format(
                Locale.US,
                "%.6f",
                result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
            )

            facilityLng = String.format(
                Locale.US,
                "%.6f",
                result.data?.getDoubleExtra("lng", 0.0) ?: 0.0
            )
            // Update UI text if dialog is open
            activePickLocationView?.text =
                "Location picked (${facilityLat}, ${facilityLng})"
        }
    }

// Secondary Address

    private var activePickLocationViewAdd: TextView? = null
    private fun showAddAddressDialog(fieldId: String,title : String) {

        val dialog = BottomSheetDialog(
            this,
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )

        dialog.setContentView(R.layout.dialog_add_secondary_address)

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        dialog.show()

        dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let {
            BottomSheetBehavior.from(it).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isHideable = false
            }
        }

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)!!
        val etAddress = dialog.findViewById<EditText>(R.id.etAddress)!!
        val tvPickLocationAdd = dialog.findViewById<TextView>(R.id.tvPickLocationAdd)!!
        val etLandmarkAdd = dialog.findViewById<EditText>(R.id.etLandmarkAdd)!!
        val btnSave = dialog.findViewById<TextView>(R.id.btnSave)!!
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)!!

        tvTitle.text = title

        // reset global values
        addressLat = null
        addressLng = null
        activePickLocationViewAdd = tvPickLocationAdd

        tvPickLocationAdd.setOnClickListener {
            secondaryAddressMapLauncher.launch(
                Intent(this, MapPickerActivity::class.java)
            )
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {

            val address = etAddress.text.toString().trim()
            val landmark = etLandmarkAdd.text.toString().trim()

            when {

                address.isBlank() ->  Toast.makeText(this@DynamicFormActivity,"Please enter address", Toast.LENGTH_SHORT).show()

                addressLat.isNullOrBlank() || addressLng.isNullOrBlank() ->
                    Toast.makeText(this@DynamicFormActivity,"Please pick location", Toast.LENGTH_SHORT).show()

                else -> {

                    val facility = FormViewModel.SecondaryAddress(
                        address = address,
                        latitude = addressLat!!,
                        longitude = addressLng!!,
                        landmark = landmark
                    )

                    vm.addAddress(fieldId, facility)
                    dialog.dismiss()
                }
            }
        }
    }
    private val secondaryAddressMapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == RESULT_OK && result.data != null) {


                addressLat = String.format(
                    Locale.US,
                    "%.6f",
                    result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
                )

                addressLng = String.format(
                    Locale.US,
                    "%.6f",
                    result.data?.getDoubleExtra("lng", 0.0) ?: 0.0
                )
                // Update UI text if dialog is open
                activePickLocationViewAdd?.text =
                    "Location picked (${addressLat}, ${addressLng})"
            }
        }


}
