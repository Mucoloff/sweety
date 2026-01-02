package dev.sweety.core.persistence.config.adapters;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.*;

public class ColorAdapter extends GsonAdapter<Color> {

    public ColorAdapter() {
        super(Color.class,
                (jsonElement, typeOfT, context) -> {
                    if (!jsonElement.isJsonObject()) return new Color(jsonElement.getAsInt(), true);

                    JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    int r = getAsIntOrDefault(asJsonObject.get("r"), 255);
                    int g = getAsIntOrDefault(asJsonObject.get("g"), 255);
                    int b = getAsIntOrDefault(asJsonObject.get("b"), 255);
                    int a = getAsIntOrDefault(asJsonObject.get("a"), 255);
                    Preconditions.checkArgument(r >= 0 && r <= 255);
                    Preconditions.checkArgument(g >= 0 && g <= 255);
                    Preconditions.checkArgument(b >= 0 && b <= 255);
                    Preconditions.checkArgument(a >= 0 && a <= 255);
                    return new Color(r, g, b, a);
                },
                (src, typeOfSrc, context) -> new JsonPrimitive(src.getRGB()));
    }
}
