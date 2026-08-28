package dev.sweety.util.logger.backend;

import dev.sweety.color.AnsiColor;
import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.formatter.LogFormatter;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.formatter.SimpleLogFormatter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileBackend implements LoggerBackend {

    private final Writer fileWriter;
    private final LogFormatter formatter;

    public FileBackend(Path file) throws IOException {
        this(file, new SimpleLogFormatter());
    }

    public FileBackend(Path file, LogFormatter formatter) throws IOException {
        this(Files.newBufferedWriter(file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND),
                formatter);
    }

    public FileBackend(Writer fileWriter, LogFormatter formatter) {
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
            String formattedLine = formatter.format(event.level(), event.loggerName(), event.rawArgs());
            fileWriter.append(AnsiColor.clear(formattedLine)).append('\n');
            fileWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
