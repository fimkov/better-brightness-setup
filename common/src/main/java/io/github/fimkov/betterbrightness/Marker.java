package io.github.fimkov.betterbrightness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Marker {
    private Marker() {}

    private static Path markerPath(Path configDir) {
        return configDir.resolve("betterbrightness").resolve(".done");
    }

    public static boolean isDone(Path configDir) {
        return Files.exists(markerPath(configDir));
    }

    public static void markDone(Path configDir) {
        Path p = markerPath(configDir);
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p)) Files.createFile(p);
        } catch (IOException e) {
            System.err.println("[betterbrightness] could not write marker: " + e);
        }
    }
}
