package dev.sweety.logger.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.logger.LogLevel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public record FileBackend(FileWriter fileWriter) implements LoggerBackend {

    public FileBackend(File file) throws IOException {
        this(new FileWriter(file));
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
