package dev.sweety.project.getters;

import dev.sweety.record.RecordGetter;

@RecordGetter
public class ExampleClass implements ExampleClassGetters {

    private int id;
    private String name;
    private byte[] data;

    @RecordGetter
    private static final String example = "example";

    public static void main(String[] args) {
        ExampleClass c = new ExampleClass();

        c.id();

        ExampleClassGetters.example();
    }

}
