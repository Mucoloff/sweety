package dev.sweety.data;

public enum Test implements PrettyEnum {
    ABC,DEF_GHI
    ;


    public static void main(String[] args) {
        for (Test value : values()) {
            System.out.println(value.name() + " - " + value.prettyName() + " - " + value.camelName());
        }
    }

}
