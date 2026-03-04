package dev.sweety.core.config.adapters;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import dev.sweety.core.color.EColor;


public class NewEColorAdapter extends GsonAdapter<EColor> {

    public NewEColorAdapter() {
        super(EColor.class,
                (jsonElement, typeOfT, context) -> {
                    if (!jsonElement.isJsonObject()) return new EColor(jsonElement.getAsInt());

                    JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    int r = getAsIntOrDefault(asJsonObject.get("red"), 255);
                    int g = getAsIntOrDefault(asJsonObject.get("green"), 255);
                    int b = getAsIntOrDefault(asJsonObject.get("blue"), 255);
                    int a = getAsIntOrDefault(asJsonObject.get("alpha"), 255);
                    Preconditions.checkArgument(r >= 0 && r <= 255);
                    Preconditions.checkArgument(g >= 0 && g <= 255);
                    Preconditions.checkArgument(b >= 0 && b <= 255);
                    Preconditions.checkArgument(a >= 0 && a <= 255);
                    return new EColor(r, g, b, a);
                },
                (src, typeOfSrc, context) -> {
                    final JsonObject obj = new JsonObject();
                    obj.addProperty("red", src.getR() & 0xFF);
                    obj.addProperty("green", src.getG() & 0xFF);
                    obj.addProperty("blue", src.getB() & 0xFF);
                    obj.addProperty("alpha", src.getA() & 0xFF);
                    return obj;
                });
    }
}
