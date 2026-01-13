package dev.sweety.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.sweety.core.util.ObjectUtils;
import dev.sweety.core.config.adapters.GsonAdapters;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GsonUtils {

    private final Gson gson = ObjectUtils.make(() -> {
        final GsonBuilder builder = new Gson().newBuilder();
        for (GsonAdapters value : GsonAdapters.VALUES) {
            value.register(builder);
        }
        return builder.disableHtmlEscaping().setPrettyPrinting().create();
    });

    public Gson gson() {
        return gson;
    }

}
