package dev.sweety.core.math.pointer;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class Ref<T> {
    public T value;

    @Override
    public String toString() {
        return value.toString();
    }
}
