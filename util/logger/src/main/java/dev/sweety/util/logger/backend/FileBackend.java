package dev.sweety.util.logger.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.formatter.LogFormatter;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.formatter.SimpleLogFormatter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileBackend implements LoggerBackend {

    private final FileWriter fileWriter;
    private final LogFormatter formatter;

    public FileBackend(File file) throws IOException {
        this(new FileWriter(file), new SimpleLogFormatter());
    }

    public FileBackend(FileWriter fileWriter, LogFormatter formatter) {
        this.fileWriter = fileWriter;
        this.formatter = formatter;
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return true;
    }

    @Override
    public void log(LogEvent event) {
        try {
            String formattedLine = formatter.format(event.getLevel(), event.getLoggerName(), event.getProfile(), event.getRawArgs());
            fileWriter.append(AnsiColor.clear(formattedLine)).append('\n');
            fileWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
