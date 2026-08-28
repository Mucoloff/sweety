package dev.sweety.media.platform.linux;

import dev.sweety.media.data.AudioStream;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeWireMonitorTest {

    /** Trimmed from real `pactl -f json list sink-inputs` output. */
    private static final String SAMPLE = """
            [
              {
                "index": 497,
                "corked": false,
                "mute": false,
                "volume": {
                  "front-left":  {"value": 47107, "value_percent": "72%"},
                  "front-right": {"value": 47107, "value_percent": "72%"}
                },
                "properties": {
                  "application.name": "Brave",
                  "application.process.id": "10372",
                  "application.process.binary": "brave",
                  "media.class": "Stream/Output/Audio"
                }
              },
              {
                "index": 473,
                "corked": true,
                "mute": false,
                "volume": {"front-left": {"value_percent": "25%"}},
                "properties": {
                  "node.name": "java",
                  "media.class": "Stream/Output/Audio"
                }
              },
              {
                "index": 12,
                "corked": false,
                "mute": false,
                "volume": {"front-left": {"value_percent": "100%"}},
                "properties": {
                  "application.name": "Recorder",
                  "media.class": "Stream/Input/Audio"
                }
              }
            ]
            """;

    @Test
    void parsesOutputStreamsAndSkipsInputs() {
        List<AudioStream> streams = PipeWireMonitor.parse(SAMPLE);

        assertEquals(2, streams.size(), "the capture stream must be skipped");

        AudioStream brave = streams.getFirst();
        assertEquals(497, brave.index());
        assertEquals(10372, brave.pid());
        assertEquals("Brave", brave.appName());
        assertEquals("brave", brave.binary());
        assertEquals(72, brave.volumePercent());
        assertTrue(brave.isActive());
    }

    @Test
    void corkedStreamsAreNotActive() {
        AudioStream corked = PipeWireMonitor.parse(SAMPLE).get(1);

        assertEquals(-1, corked.pid(), "no process id in the properties");
        assertFalse(corked.isActive());
    }

    @Test
    void toleratesEmptyOutput() {
        assertTrue(PipeWireMonitor.parse("[]").isEmpty());
        assertTrue(PipeWireMonitor.parse("{}").isEmpty());
    }
}
