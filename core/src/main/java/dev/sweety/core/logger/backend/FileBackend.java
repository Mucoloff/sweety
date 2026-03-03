package dev.sweety.core.logger.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.LogLevel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileBackend implements LoggerBackend {

    private final FileWriter fileWriter;

    public FileBackend(File file) {
        try {
            this.fileWriter = new FileWriter(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void log(LogLevel level, String loggerName, String profile, String formattedLine) {
        try {
            fileWriter.append(AnsiColor.clear(formattedLine));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
