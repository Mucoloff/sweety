package dev.sweety.persistence.config.adapters;

import com.google.gson.JsonPrimitive;
import dev.sweety.core.color.EColor;

public class EColorAdapter extends GsonAdapter<EColor> {

    public EColorAdapter() {
        super(EColor.class,
                (jsonElement, typeOfT, context) -> new EColor(jsonElement.getAsInt()),
                (src, typeOfSrc, context) -> new JsonPrimitive(src.getRgba()));
    }
}
