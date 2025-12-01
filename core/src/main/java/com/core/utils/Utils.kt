package com.core.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.core.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.core.utils.CalenderFormat as CalenderFormat1


class Utils {

    companion object {

        val job = SupervisorJob()
        val coroutineContext = Dispatchers.Main + job

       val CHILDREN_AL_ID = 708
           val FOOD_COUPAN_ID = 705
           val HRA_ID = 703
        @JvmStatic
        val screenWidth = 750 //Resources.getSystem().displayMetrics.widthPixels

        @JvmStatic
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        @JvmStatic
        fun  roundup( amt:String): String{

            if(!TextUtils.isEmpty(amt)){
                try {
                    val  amt : Double = amt.toDouble()
                    val roundUpamt =  Math.round(amt)
                    return roundUpamt.toString()
                } catch (e: Exception) {
                }
            }else{
                return "0"
            }

            return "0";
        }
        //val screenDensity = Resources.getSystem().displayMetrics.density
        val screenDensity = 1f

        fun getStatusBarHeight(): Int {
            var result = 0
            val resourceId: Int =
                Resources.getSystem().getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = Resources.getSystem().getDimensionPixelSize(resourceId)
            }
            return result
        }




        @JvmStatic
        fun getApplicationName(context: Context): String {
            val applicationInfo = context.applicationInfo
            val stringId = applicationInfo.labelRes
            return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(
                stringId
            )
        }


        fun showKeyboard(view: View) {
            view.requestFocus()
            if (!isHardKeyboardAvailable(view)) {
                val inputMethodManager =
                    view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(view, 0)
            }
        }

        @JvmStatic
        fun isKeyboardOpened(view: View): Boolean {
            val imm = view.context
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            if (imm.isAcceptingText) {
                DebugLog.e("Software Keyboard was shown")
                return true
            } else {
                DebugLog.e("Software Keyboard was not shown")
                return false
            }
        }

        private fun isHardKeyboardAvailable(view: View): Boolean {
            return view.context.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
        }

        fun hideKeyboard(activity: Activity) {
            try {
                val inputManager =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (activity.currentFocus!!.windowToken != null)
                    inputManager.hideSoftInputFromWindow(
                        activity.currentFocus!!.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
            } catch (e: Exception) {
            }
        }

        @JvmStatic
        fun hideKeyboard(view: View) {
            try {
                val inputManager =
                    view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (view.windowToken != null)
                    inputManager.hideSoftInputFromWindow(
                        view.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
            } catch (e: Exception) {
            }
        }



        fun getMd5String(stringToConvert: String): String {
            try {
                // Create MD5 Hash
                val digest = java.security.MessageDigest
                    .getInstance("MD5")
//                        .getInstance("MD5")
                digest.update(stringToConvert.toByteArray())
                val messageDigest = digest.digest()

                // Create Hex String
                val hexString = StringBuilder()
                for (aMessageDigest in messageDigest) {
                    var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                    while (h.length < 2)
                        h = "0$h"
                    hexString.append(h)
                }
                return hexString.toString()

            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            return ""
        }

        fun sha256(base: String): String? {
            return try {
                val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
                val hash: ByteArray = digest.digest(base.toByteArray(charset("UTF-8")))
                val hexString = java.lang.StringBuilder()
                for (i in hash.indices) {
                    val hex = Integer.toHexString(0xff and hash[i].toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }
                hexString.toString()
            } catch (ex: java.lang.Exception) {
                throw RuntimeException(ex)
                ""
            }
        }





         /**
         * get Current Date in Date object
         *
         * @return Date object
         */
        val todayDate: Date
            get() {
                val calToday = Calendar.getInstance()
                calToday.set(Calendar.HOUR_OF_DAY, 0)
                calToday.set(Calendar.MINUTE, 0)
                calToday.set(Calendar.SECOND, 0)
                calToday.set(Calendar.MILLISECOND, 0)
                return calToday.time
            }

        /**
         * Convert String in Date object
         *
         * @return Date object
         */
        fun convertStringToDateWithLocale(
            strDate: String,
            parseFormat: String,
            locale: Locale
        ): Date? {
            return try {
                SimpleDateFormat(parseFormat, locale).parse(strDate)
            } catch (e: Exception) {
                DebugLog.print(e)
                null
            }
        }




        fun getFileSize(filePath: String): Long {
            val file = File(filePath)
            val fileSizeInKB = file.length() / 1024
            return fileSizeInKB / 1024
        }

        fun findWordFromString(
            str: String,
            offsetLength: Int
        ): String { // when you touch ' ', this method returns left word.
            var offset = offsetLength
            if (str.length == offset) {
                offset-- // without this code, you will get exception when touching end of the text
            }

            if (str[offset] == ' ') {
                offset--
            }
            var startIndex = offset
            var endIndex = offset

            try {
                while (str[startIndex] != ' ' && str[startIndex] != '\n') {
                    startIndex--
                }
            } catch (e: StringIndexOutOfBoundsException) {
                startIndex = 0
            }

            try {
                while (str[endIndex] != ' ' && str[endIndex] != '\n') {
                    endIndex++
                }
            } catch (e: StringIndexOutOfBoundsException) {
                endIndex = str.length
            }

            // without this code, you will get 'here!' instead of 'here'
            // if you use only english, just check whether this is alphabet,
            // but 'I' use korean, so i use below algorithm to get clean word.
            val last = str[endIndex - 1]
            if (last == ',' || last == '.' ||
                last == '!' || last == '?' ||
                last == ':' || last == ';'
            ) {
                endIndex--
            }
            return str.substring(startIndex, endIndex)
        }


        @JvmStatic
        fun calToFormattedDate(calToDate: Calendar?, format: CalenderFormat1): String {
            if (calToDate == null) {
                return ""
            }
            val sdf = SimpleDateFormat(format.type, Locale.getDefault())
            return sdf.format(calToDate.time)
        }

        @JvmStatic
        fun calToFormattedDateUTC(calToDate: Calendar?, format: CalenderFormat1): String {
            if (calToDate == null) {
                return ""
            }
            val sdf = SimpleDateFormat(format.type, Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(calToDate.time)
        }


        @JvmStatic
        fun dateToCalendar(date: String?, format: CalenderFormat1): Calendar {
            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat(format.type, Locale.getDefault())
            try {
                cal.time = sdf.parse(date)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return cal
        }

        @JvmStatic
        fun changeDateFormat(
            date: String?,
            fromFormat: CalenderFormat1,
            toFormat: CalenderFormat1
        ): String {
            if (date == null) {
                return ""
            }
            try {
                val date = SimpleDateFormat(fromFormat.type, Locale.US).parse(date)
                return SimpleDateFormat(toFormat.type, Locale.US).format(date)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

        @JvmStatic
        fun changeDateFormatUTC(
            date: String?,
            fromFormat: CalenderFormat1,
            toFormat: CalenderFormat1
        ): String {
            if (date == null) {
                return ""
            }
            try {
                val formatter = SimpleDateFormat(fromFormat.type, Locale.US)
                val date = formatter.parse(date)
                val formatter1 = SimpleDateFormat(toFormat.type, Locale.US)
                formatter1.timeZone = TimeZone.getTimeZone("UTC")
                return formatter1.format(date)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

        @JvmStatic
        fun changeDateFormatFromUTC(
            date: String?,
            fromFormat: CalenderFormat1,
            toFormat: CalenderFormat1
        ): String {
            if (date == null) {
                return ""
            }
            try {
                val formatter = SimpleDateFormat(fromFormat.type, Locale.US)
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                val date = formatter.parse(date)
                val formatter1 = SimpleDateFormat(toFormat.type, Locale.US)
                return formatter1.format(date)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }


        fun isValidPhoneNumber(s: String): Boolean {

            val p = Pattern.compile("^[0-9]{10,}$")
            val m = p.matcher(s)
            return m.find() && m.group() == s
        }

        fun isEmailValid(email: String): Boolean {
            return Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }

        fun isValidPassword(password: String): Boolean {
            val pattern: Pattern
            val matcher: Matcher
            val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\W)(?=.*[A-Za-z])(?=.*?[0-9]).{8,}"
            pattern = Pattern.compile(passwordPattern)
            matcher = pattern.matcher(password)
            return matcher.matches()
        }

        fun isEmailorPhoneNumber(s: String): Boolean { //true -> email, false-> mobile number
            return s.contains("@") && s.contains(".")
        }



        /**
         * Remove [] from Error Objects when there are multiple errors
         *
         * @param message as String
         * @return replacedString
         */
        fun removeArrayBrace(message: String): String {
            return message.replace("[\"", "").replace("\"]", "").replace(".", "")
        }

        /***
         * Function that checks if network is available
         * @return true if network available otherwise false
         */


        fun getJsonFromAssets(context: Context, fileName: String?): String {
            val jsonString: String
            jsonString = try {
                val `is`: InputStream = context.assets.open(fileName!!)
                val size: Int = `is`.available()
                val buffer = ByteArray(size)
                `is`.read(buffer)
                `is`.close()
                String(buffer, Charset.forName("UTF-8"))
            } catch (e: IOException) {
                e.printStackTrace()
                return "null"
            }
            return jsonString
        }



         fun getAge(year: Int, month: Int, day: Int): Boolean {
            try {
                val dob = Calendar.getInstance()
                val today = Calendar.getInstance()
                dob[year, month] = day
                val monthToday = today[Calendar.MONTH] + 1
                val monthDOB = dob[Calendar.MONTH] + 1
                val age = today[Calendar.YEAR] - dob[Calendar.YEAR]
                return if (age > 18) {
                    true
                } else if (age == 18) {
                    if (monthDOB > monthToday) {
                        true
                    } else if (monthDOB == monthToday) {
                        val todayDate = today[Calendar.DAY_OF_MONTH]
                        val dobDate = dob[Calendar.DAY_OF_MONTH]
                        if (dobDate <= todayDate) { // should be less then
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return false
        }

        fun getIPAddress(useIPv4: Boolean): String? {
            try {
                val interfaces: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs: List<InetAddress> = Collections.list(intf.getInetAddresses())
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress()) {
                            val sAddr: String = addr.getHostAddress()!!
                            //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (useIPv4) {
                                if (isIPv4) return sAddr
                            } else {
                                if (!isIPv4) {
                                    val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                                    return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                        0,
                                        delim
                                    ).uppercase(
                                        Locale.getDefault()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
            } // for now eat exceptions
            return ""
        }

        private var progressDialog: Dialog? = null

        /**
         * Show Progress Dialog
         * @param mContext Context
         */
        fun showProgressDialog(mContext: Context) {
            hideProgressDialog()
            progressDialog = Dialog(mContext)
            progressDialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            progressDialog?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
            progressDialog?.window?.setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(mContext, android.R.color.transparent)
                )
            )
            val lp = progressDialog?.window?.attributes
            lp?.dimAmount = 0.8f // Dim level. 0.0 - no dim, 1.0 - completely opaque
            progressDialog?.window?.attributes = lp
            progressDialog?.setCancelable(false)
            val viewChild = View.inflate(mContext, R.layout.layout_progress_loader, null)
            progressDialog?.setContentView(viewChild)
            try {
                progressDialog?.show()
            } catch (e: Exception) {
                DebugLog.print(e)
            }
        }

        /**
         * Hide Progress bar
         */
        fun hideProgressDialog() {
            try {
                progressDialog?.let {
                    if (it.isShowing) {
                        it.dismiss()
                    }
                }
            } catch (e: Exception) {
                DebugLog.print(e)
            }
        }


    }





}