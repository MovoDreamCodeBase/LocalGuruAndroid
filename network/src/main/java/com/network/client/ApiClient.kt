package com.network.client

import android.util.Log
import com.core.constants.AppConstants


import com.core.utils.DebugLog
import com.google.gson.JsonIOException
import com.network.BuildConfig
import com.network.api.ApiInterface
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*


class ApiClient {

    companion object {

        private var okHttpClient: OkHttpClient? = null

        var retrofits: Retrofit? = null

        var myapiInterface: ApiInterface? = null


        /**
         * This is the generic method which will create retrofit object as singleton.
         */
        fun initRetrofit() {
            if (retrofits == null) {
                retrofits = getRetrofit()
                myapiInterface = retrofits?.create(ApiInterface::class.java)!!
            }
        }


        /**
         * Return API interface
         *
         */
        fun getApiInterface(): ApiInterface {
            if (myapiInterface != null) {
                return myapiInterface!!
            }
            myapiInterface = retrofits?.create(ApiInterface::class.java)!!
            return myapiInterface as ApiInterface
        }


        /**
         * Generate Retrofit Client
         */
        private fun getRetrofit(): Retrofit {
            val builder = Retrofit.Builder()
            builder.baseUrl(BuildConfig.BASE_URL)
            builder.addConverterFactory(GsonConverterFactory.create())
            //  builder.addCallAdapterFactory(CoroutineCallAdapterFactory())
            builder.client(getOkHttpClient())
            return builder.build()
        }


        /**
         * generate OKhttp client
         */
        private fun getOkHttpClient(): OkHttpClient {
            if (okHttpClient == null) {
                val builder = OkHttpClient.Builder()

                builder.addInterceptor(Interceptor { chain ->
                    val request =
                        chain.request().newBuilder().addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "application/json")
                            .addHeader(
                                "Authorization",
                                HttpCommonMethod.getToken()?.trim().toString()
                            )
                            .build()
                    chain.proceed(request)
                })
                    .protocols(Arrays.asList(Protocol.HTTP_1_1))
                    .readTimeout(1, TimeUnit.MINUTES)
                    .connectTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)

                    .build()


                if (AppConstants.LOGGER_ENABLED) {
                    val logging = HttpLoggingInterceptor()
                    logging.level = HttpLoggingInterceptor.Level.HEADERS
                    logging.level = HttpLoggingInterceptor.Level.BODY

                    if (BuildConfig.DEBUG) {
                        builder.addInterceptor(logging)
                    }
                }
                // Create a trust manager that does not validate certificate chains
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        @Throws(CertificateException::class)
                        override fun checkClientTrusted(
                            chain: Array<X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        @Throws(CertificateException::class)
                        override fun checkServerTrusted(
                            chain: Array<X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                            return arrayOf()
                        }
                    }
                )

                // Install the all-trusting trust manager
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                // Create an ssl socket factory with our all-trusting manager
                val sslSocketFactory = sslContext.socketFactory
                val trustManagerFactory: TrustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers: Array<TrustManager> =
                    trustManagerFactory.trustManagers
                check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
                    "Unexpected default trust managers:" + trustManagers.contentToString()
                }

                val trustManager =
                    trustManagers[0] as X509TrustManager
                builder.sslSocketFactory(sslSocketFactory, trustManager)
                okHttpClient = builder.build()

            }
            return okHttpClient!!
        }


        /**
         * generate custom response for exception
         */
        fun generateCustomResponse(code: Int, message: String, request: Request): Response? {
            try {
                val body = getJSONObjectForException(message, code).toString()
                    .toResponseBody("application/json".toMediaTypeOrNull())
                return Response.Builder()
                    .code(code)
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .body(body)
                    .message(message)
                    .build()
            } catch (ex: JsonIOException) {
                Log.d("EX", ex.message!!)
                return null
            }

        }

        /**
         * generate JSON object for error case
         */
        private fun getJSONObjectForException(message: String, code: Int): JSONObject {

            try {
                val jsonMainObject = JSONObject()

                val `object` = JSONObject()
                `object`.put("status", false)
                `object`.put("message", message)
                `object`.put("message_code", code)
                `object`.put("status_code", code)

                jsonMainObject.put("meta", `object`)

                val obj = JSONObject()
                obj.put("error", JSONArray().put(message))

                jsonMainObject.put("errors", obj)

                return jsonMainObject
            } catch (e: JSONException) {
                DebugLog.print(e)
                return JSONObject()
            }
        }


    }


}
