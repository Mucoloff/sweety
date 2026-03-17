package dev.sweety.patch.archive;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarArchive implements Archive {
    
    private final File file;

    public JarArchive(File file) {
        this.file = file;
    }

    @Override
    public Map<String, byte[]> entries() {
        Map<String, byte[]> entries = new HashMap<>();
        
        if (!file.exists()) {
            throw new RuntimeException("Archive file does not exist: " + file.getAbsolutePath());
        }

        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> jarEntries = jar.entries();
            
            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                
                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                // Read file content
                try (InputStream is = jar.getInputStream(entry)) {
                    entries.put(entry.getName(), readAllBytes(is));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JAR archive: " + file.getAbsolutePath(), e);
        }

        return entries;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}