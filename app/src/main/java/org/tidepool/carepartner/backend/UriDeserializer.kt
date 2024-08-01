package org.tidepool.carepartner.backend

import android.net.Uri
import com.google.gson.*
import java.lang.reflect.Type

class UriDeserializer : JsonSerializer<Uri>, JsonDeserializer<Uri> {
    
    override fun serialize(
        src: Uri,
        typeOfSrc: Type?,
        context: JsonSerializationContext
    ): JsonElement {
        return context.serialize(src.toString(), String::class.java)
    }
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext
    ): Uri {
        return Uri.parse(context.deserialize(json, String::class.java))
    }
}