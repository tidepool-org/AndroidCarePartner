package org.tidepool.carepartner

import android.app.Application
import android.content.Context
import android.util.Log
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.getAccessToken
import org.tidepool.sdk.TidepoolSDK
import org.tidepool.sdk.TokenProvider

class TidepoolApplication : Application() {
    
    companion object {
        
        private const val TAG = "TidepoolApplication"
        
        @Volatile
        private var INSTANCE: TidepoolSDK? = null
        
        fun getTidepoolSDK(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: createTidepoolSDK(context).also { INSTANCE = it }
        }
        
        private fun createTidepoolSDK(context: Context) = TidepoolSDK(
            environment = PersistentData.environment,
            tokenProvider = object : TokenProvider {
                override suspend fun getToken() = context.getAccessToken()
            },
        )
        
        fun shutdownSDK() {
            synchronized(this) {
                INSTANCE?.shutdown()
                INSTANCE = null
                Log.d(TAG, "TidepoolSDK manually shutdown")
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")
        // Initialize SDK early
        getTidepoolSDK(this)
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminating - shutting down TidepoolSDK")
        INSTANCE?.shutdown()
        INSTANCE = null
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Trim memory: level $level")
        
        // If the app is being killed due to memory pressure, shut down the SDK
        if (level >= TRIM_MEMORY_COMPLETE) {
            Log.d(TAG, "App being killed - shutting down TidepoolSDK")
            INSTANCE?.shutdown()
            INSTANCE = null
        }
    }
}