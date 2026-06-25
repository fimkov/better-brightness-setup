package io.github.fimkov.betterbrightness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MarkerTest {
    @Test void absentThenPresent(@TempDir Path cfg) {
        assertFalse(Marker.isDone(cfg));
        Marker.markDone(cfg);
        assertTrue(Marker.isDone(cfg));
    }
    @Test void markDoneIsIdempotent(@TempDir Path cfg) {
        Marker.markDone(cfg);
        Marker.markDone(cfg);
        assertTrue(Marker.isDone(cfg));
    }
}
