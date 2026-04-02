package dev.sweety.data;

public interface PrettyEnum {

    String name();

    default String prettyName() {
        return this.name().toLowerCase();
    }

    default String camelName() {
        String[] parts = this.name().toLowerCase().split("_");
        StringBuilder camelCaseName = new StringBuilder();
        for (String part : parts) {
            camelCaseName.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return camelCaseName.toString();
    }

}
