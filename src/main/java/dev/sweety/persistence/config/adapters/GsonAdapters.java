package dev.sweety.persistence.config.adapters;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum GsonAdapters {
    COLOR(new ColorAdapter()),
    ECOLOR(new EColorAdapter())
    ;

    public static final GsonAdapters[] VALUES = values();

    private final GsonAdapter<?> adapter;

    <T> GsonAdapters(Class<T> type, JsonDeserializer<T> deserializer, JsonSerializer<T> serializer) {
        this.adapter = new GsonAdapter<>(type, deserializer, serializer);
    }

    public void register(GsonBuilder builder) {
        this.adapter.register(builder);
    }

}

