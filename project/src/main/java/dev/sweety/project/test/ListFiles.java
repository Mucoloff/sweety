package dev.sweety.project.test;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

public class ListFiles {

    final PrintStream out;

    ListFiles(final File o) throws FileNotFoundException {
        this.out = new PrintStream(o);
    }

    @NotNull
    private File[] list(File file) {
        File[] files = file.listFiles();
        return files != null ? files : new File[0];
    }

    private void printFiles(File file, String indent) {
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

        ListFiles listFiles = new ListFiles(o);


        for (String s : new String[]{"core", "event", "event-processor", "loadbalancer", "minecraft", "module", "netty", "packet-processor", "project", "record-getter-processor", "spotify"}) {
            listFiles.printFiles(new File("/home/sweety/projects/java/sweety/" + s + "/src/main/java/dev/sweety/"), "  ");
        }

    }

}
