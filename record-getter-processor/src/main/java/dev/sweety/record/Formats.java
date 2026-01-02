package dev.sweety.record;

public enum Formats {
    GETTER("""
            default %field-type% %field-name%(){
                return ((%class%) this).%field-name%;
            }
            """),


    SETTER("""
            default void %field-name%(%field-type% %field-name%){
                ((%class%) this).%field-name% = %field-name%;
            }
            """),

    STATIC_GETTER("""
            static %field-type% %field-name%(){
                return (%field-type%) %class%.%name%;
            }
            """),

    STATIC_SETTER("""
            static void %field-name%(%field-type% %field-name%){
                %class%.%name% = %name%;
            }
            """),

    PRIVATE_GETTER("""
            @SneakyThrows
            default %field-type% %field-name%(){
                return (%field-type%) DataUtils.get(%class%.class, "%field-name%", this);
            }
            """),

    PRIVATE_SETTER("""
            @SneakyThrows
            default void %field-name%(%field-type% %field-name%){
                DataUtils.set(%class%.class, "%field-name%", this, %field-name%);
            }
            """),

    PRIVATE_STATIC_GETTER("""
            @SneakyThrows
            static %field-type% %field-name%(){
                return (%field-type%) DataUtils.get(%class%.class, "%field-name%");
            }
            """),

    PRIVATE_STATIC_SETTER("""
            @SneakyThrows
            static void %field-name%(%field-type% %field-name%){
                DataUtils.set(%class%.class, "%field-name%", %field-name%);
            }
            """),

    ;

    private final String code;

    Formats(final String code) {
        this.code = code;
    }

    public String apply(String fieldType, String fieldName, String className) {
        return code
                .replace("%field-type%", fieldType)
                .replace("%field-name%", fieldName)
                .replace("%class%", className);
    }

}
