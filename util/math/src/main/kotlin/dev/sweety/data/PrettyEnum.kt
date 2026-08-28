package dev.sweety.data

interface PrettyEnum {

    fun prettyName(): String = (this as Enum<*>).name.lowercase()

    fun camelName(): String {
        val parts = prettyName().split("_")
        val camelCaseName = StringBuilder()
        for (part in parts) {
            camelCaseName.append(part.substring(0, 1).uppercase()).append(part.substring(1))
        }
        return camelCaseName.toString()
    }
}
