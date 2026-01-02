package dev.sweety.core.persistence.config.adapters;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GsonAdapter<T> {

    final Class<T> type;
    final JsonDeserializer<T> deserializer;
    final JsonSerializer<T> serializer;

    protected static int getAsIntOrDefault(JsonElement el, int defaultVal) {
        return el == null ? defaultVal : el.getAsInt();
    }

    public void register(GsonBuilder builder) {
        builder.registerTypeAdapter(type, deserializer);
        builder.registerTypeAdapter(type, serializer);
    }
}