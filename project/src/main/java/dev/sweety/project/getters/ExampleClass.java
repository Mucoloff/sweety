package dev.sweety.project.getters;

import dev.sweety.record.annotations.*;

import java.io.File;
import java.util.UUID;

@RecordData(includeStatic = true, setterTypes = Setter.Type.BUILDER)
public class ExampleClass {

    private int id;
    String name;

    private static final String example = "example";

    private static String t1 = "t1";

    public static void main(String[] args) {
        ExampleClass c = new ExampleClass();

        //new ExampleClass();

        System.out.println("ID: " + c.id());
        System.out.println("Name: " + c.name());

        c.setId(10).setName("Test Name");

        System.out.println("ID: " + c.id());
        System.out.println("Name: " + c.name());


        System.out.println("Static T1: " + ExampleClass.t1());
        ExampleClass.setT1("New T1");
        System.out.println("Static T1 Modified: " + ExampleClass.t1());

        c.excepthandler();
    }


    @SneakyThrows
    public void excepthandler() {
        File f = new File("test");
        f.createNewFile();
    }

}
