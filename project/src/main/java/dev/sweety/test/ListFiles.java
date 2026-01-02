package dev.sweety.test;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

public class ListFiles {

    static PrintStream out;

    @NotNull
    private static File[] list(File file) {
        File[] files = file.listFiles();
        return files != null ? files : new File[0];
    }

    private static void printFiles(File file, String indent) {
        File[] children = list(file);
        Arrays.sort(children, java.util.Comparator.comparing(File::getName));
        for (int i = 0; i < children.length; i++) {
            File f = children[i];
            boolean isLast = i == children.length - 1;
            String prefix = isLast ? "└── " : "├── ";
            if (f.isDirectory()) {
                out.println(indent + prefix + "📁 " + f.getName() + "/");
                printFiles(f, indent + (isLast ? "    " : "│   "));
            } else {
                out.println(indent + prefix + "📄 " + f.getName());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File o = new File("file_tree.txt");
        if (!o.exists()) {
            o.createNewFile();
        }

        out = new PrintStream(o);

        for (String s : new String[]{"core", "event","event-processor","module","netty","project","spotify"}) {
            printFiles(new File("/home/sweety/projects/java/sweety/" + s + "/src/main/java/dev/sweety/"), "  ");
        }

    }

}
