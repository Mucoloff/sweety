package dev.sweety.project.getters;

import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.RecordGetter;
import dev.sweety.record.annotations.RecordSetter;

@RecordData
public class ExampleClass implements ExampleClassAccessors {

    private int id;
    private String name;
    private byte[] data;

    @RecordGetter
    private static final String example = "example";

    @RecordSetter
    private static String t1 = "t1";

    public static void main(String[] args) {
        ExampleClass c = new ExampleClass();

        ExampleClassAccessors.example();
        ExampleClassAccessors.t1("changed t1");

    }

}
