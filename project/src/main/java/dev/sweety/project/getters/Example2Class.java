package dev.sweety.project.getters;

import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.RecordGetter;

public class Example2Class implements Example2ClassAccessors{

    @RecordData
    private int id;

    @RecordData
    String name;
    private byte[] data;

    @RecordGetter
    private static String example = "example";

    public static void main(String[] args) {
        Example2Class c = new Example2Class();


    }

}
