package dev.sweety.project.getters;

import dev.sweety.record.annotations.RecordData;

@RecordData(includeStatic = true)
public class ExampleClass
        //implements ExampleClassAccessors todo
{

    private int id;
    String name;

    private static final String example = "example";

    private static String t1 = "t1";

    public static void main(String[] args) {
        ExampleClass c = new ExampleClass();


    }

}
