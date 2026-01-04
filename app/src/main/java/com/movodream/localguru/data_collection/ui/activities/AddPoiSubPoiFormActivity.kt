package com.movodream.localguru.data_collection.ui.activities

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
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
import com.google.android.material.card.MaterialCardView
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


class AddPoiSubPoiFormActivity : BaseActivity() {


    private lateinit var vm: FormViewModel
    private lateinit var tabRecycler: RecyclerView
    private lateinit var tabAdapter: TabAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DynamicFormAdapter

   private lateinit var txtSubmit : AppCompatTextView
    private lateinit var submitBtn: MaterialCardView

    private var currentPhotoFieldId: String? = null
    private var lastShownTabId: String? = null
    private var poiId = -1
    private  var selectedPOI: List<TaskItem>? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_poi_sub_poi_form)

        val schema : FormSchema? = intent.getParcelableExtra("KEY_SCHEMA") as FormSchema?
         selectedPOI=
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("KEY_POI", TaskItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("KEY_POI")
            }
             vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[FormViewModel::class.java]

        vm._isAddPOIState.value = intent.getBooleanExtra("KEY_IS_ADD_POI",false)

        permissionUtils = PermissionUtils(this)
        recycler = findViewById(R.id.recycler)
        tabRecycler = findViewById(R.id.tab_recycler)
        submitBtn = findViewById(R.id.cv_btn_submit)
        txtSubmit = findViewById(R.id.tvSubmit)

        val tvAgentProfile: AppCompatTextView=findViewById(R.id.tv_agent_profile)
        val tvAgentLocation: AppCompatTextView=findViewById(R.id.tv_agent_location)
        val tvAgentName: AppCompatTextView=findViewById(R.id.tv_agent_name)
        if(vm._isAddPOIState.value==true){
            txtSubmit.text =getString(R.string.add_poi)
        }
        tvAgentName.text = selectedPOI!!.get(0).agentName
        tvAgentLocation.text = selectedPOI!!.get(0)!!.agentLocation +" Data Collector"

        if (selectedPOI!!.get(0).agentName.isNullOrBlank()) {
            tvAgentProfile.text = ""

        }else {

            val parts = selectedPOI!!.get(0).agentName.trim().split(" ")

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
                 //   vm.removePhotoUri(fieldId, uri.toString())
                //  Local photo
                vm.removePhoto(fieldId, uriStr)
            },
            onAddNotification = onAdd@{
            },onRemoveNotification = { index ->

            }, onPreviewPhotos = { fieldId, uris ->
                showPhotoPreviewDialog(fieldId, uris)  // final call
            },onAddSubPoi = {
                           },onRemoveSubPoi = { index ->

            },  onOperationalHoursChanged = { list,c ->

            },onAddHoliday = {

            },
            onAddSeasonal = {

            },

            onRemoveHoliday = { index ->

            },
            onRemoveSeason = { index ->

            },onRequestRebind = { fieldId ->
                vm.notifyFieldChanged(fieldId) // ✅ ONLY PLACE this exists
            }


        )
        adapter.setHasStableIds(true)
        recycler.adapter = adapter





        // Observe schema
        vm.schemaLive.observe(this) { schema ->
            schema?.let {
                // Inject dropdown items for mainPOI
               vm.applyAssignedPoiOptions(selectedPOI!!)
                tabAdapter.submitTabs(it.tabs)
            }
        }

        vm.fieldChangeLive.observe(this) { fieldId ->
            refreshCurrentTab()
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
           if(vm._isAddPOIState.value==true){
               vm.createPOI(selectedPOI)
           }else {
               vm.submitSubPOI(selectedPOI)
           }

        }


        vm.loadSchemaFromString(schema)

        vm.loadDraft("" + poiId) {


            val loadedSchema = vm.schemaLive.value ?: return@loadDraft
            val firstTab = loadedSchema.tabs.first()
            showTab(firstTab.id)   // spinner restores correctly
        }
        setObserver()
    }


    private fun setObserver() {


        vm.addPOISubPOIResponse.observe(
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


                    is ResponseHandler.OnSuccessResponse<List<BulkSubPoiItem>?> -> {

                        state.response?.let {
                            Utils.hideProgressDialog()
                            CustomDialogBuilder(this)
                                .setTitle("Successful")
                                .setMessage(
                                    if (vm._isAddPOIState.value == true)
                                        "POI assigned successfully! This POI is now available on your dashboard."
                                    else
                                        "Sub-POI data submitted successfully! You can view the details of the selected POI in the Completed tab of the Tasks section."
                                )

                                .setPositiveButton("OK") {
                                    setResult(Activity.RESULT_OK)
                                    finish()  // close after draft is removed

                                }

                                .setCancelable(false)
                                .show()

                        }


                        Utils.hideProgressDialog()

                    }
                }
            })

        vm.createPOIResponse.observe(
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
                                .setMessage(
                                    if (vm._isAddPOIState.value == true)
                                        "POI assigned successfully! This POI is now available on your dashboard."
                                    else
                                        "Sub-POI data submitted successfully! You can view the details of the selected POI in the Completed tab of the Tasks section."
                                )

                                .setPositiveButton("OK") {
                                    setResult(Activity.RESULT_OK)
                                    finish()  // close after draft is removed

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
                Toast.makeText(this@AddPoiSubPoiFormActivity,"Please try again...", Toast.LENGTH_LONG).show()
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
        mapLauncher.launch(Intent(this, MapPickerActivity::class.java))
    }

    private val mapLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val lat = result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
                val lng = result.data?.getDoubleExtra("lng", 0.0) ?: 0.0

                val rawLocation = result.data?.getStringExtra("locationInfo").orEmpty().trim()
                val physicalAddress = result.data?.getStringExtra("address").orEmpty().trim()

                Log.d("ADDRESS : ", rawLocation)
                Log.d("physicalAddress : ", physicalAddress)
                val parts = if (rawLocation.isNotEmpty()) {
                    rawLocation.split("|").map { it.trim() }
                } else {
                    emptyList()
                }

                // Ensure always 3 values
                val city    = parts.getOrNull(0).orEmpty()
                val state   = parts.getOrNull(1).orEmpty()
                val country = parts.getOrNull(2).orEmpty()


                vm.updateValue("latitude", lat)
                vm.updateValue("longitude", lng)


               if(vm._isAddPOIState.value==false) {
                   vm.updateValue("physicalAddress", physicalAddress)

                   if (lat != 0.0 && lng != 0.0) {
                       vm.updateValue("pinVerifiedViaGps", "Y")
                       vm.notifyFieldChanged("pinVerifiedViaGps")
                   }
                   vm.updateValue("localityTown", city)
                   vm.updateValue("regionState", state)
                   vm.updateValue("country", country)
                   vm.notifyFieldChanged("localityTown")
                   vm.notifyFieldChanged("regionState")
                   vm.notifyFieldChanged("country")
                   vm.notifyFieldChanged("physicalAddress")
               }

                vm.notifyFieldChanged("latitude")
                vm.notifyFieldChanged("longitude")
                if(vm._isAddPOIState.value==true) {
                    vm.updateValue("address", physicalAddress)
                    vm.notifyFieldChanged("address")
                }

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

//  Extract pure URL if stored as url|id
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
        }



}