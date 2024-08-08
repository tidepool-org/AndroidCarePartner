package org.tidepool.carepartner.backend

import android.util.Log
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken.END_OBJECT
import com.google.gson.stream.JsonWriter
import org.tidepool.sdk.AuthenticationServer
import org.tidepool.sdk.Environment
import java.net.URL

private const val TAG = "EnvTypeAdapter"

class EnvironmentTypeAdapter : TypeAdapterFactory {
    private val customTypeAdapter: TypeAdapter<*> by lazy { CustomTypeAdapter() }
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (Environment::class.java.isAssignableFrom(type.rawType)) {
            Log.v(TAG, "Creating adapter for $type")
            @Suppress("UNCHECKED_CAST")
            return customTypeAdapter as TypeAdapter<T>
        }
        return null
    }
    
    class CustomTypeAdapter : TypeAdapter<Environment>() {
        
        override fun write(out: JsonWriter, value: Environment) {
            out.beginObject()
            out.name("envCode").value(value.envCode)
            out.name("url").value(value.url.toString())
            out.name("auth").beginObject()
            out.name("url").value(value.auth.url.toString())
            out.endObject()
            out.endObject()
        }
        
        override fun read(reader: JsonReader): Environment {
            var envCode: String? = null
            var url: URL? = null
            var authUrl: URL? = null
            reader.beginObject()
            while (envCode == null || url == null || authUrl == null) {
                when (reader.nextName()) {
                    "envCode" -> envCode = reader.nextString()
                    "url" -> url = URL(reader.nextString())
                    "auth" -> {
                        reader.beginObject()
                        while (authUrl == null) {
                            when (reader.nextName()) {
                                "url" -> authUrl = URL(reader.nextString())
                                else -> reader.skipValue()
                            }
                        }
                        while (reader.peek() != END_OBJECT) {
                            reader.skipValue()
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            while (reader.peek() != END_OBJECT) {
                reader.skipValue()
            }
            reader.endObject()
            Log.v(TAG, "envCode: $envCode")
            Log.v(TAG, "url: $url")
            Log.v(TAG, "authUrl: $authUrl")
            return object : Environment {
                override val envCode: String = envCode
                override val url: URL = url
                override val auth: AuthenticationServer = object : AuthenticationServer {
                    override val url: URL = authUrl
                }
            }
        }
    }
}