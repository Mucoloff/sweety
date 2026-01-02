package dev.sweety.project.getters;

import dev.sweety.record.RecordGetter;

public class Example2Class implements Example2ClassGetters {

    @RecordGetter
    private int id;

    @RecordGetter
    String name;
    private byte[] data;

    @RecordGetter
    private static final String example = "example";

    public static void main(String[] args) {
        Example2Class c = new Example2Class();


    }

}
