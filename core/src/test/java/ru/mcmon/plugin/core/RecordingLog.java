package ru.mcmon.plugin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecordingLog implements Log {

    final List<String> lines = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void info(String message) {
        lines.add("INFO " + message);
    }

    @Override
    public void warn(String message) {
        lines.add("WARN " + message);
    }

    String text() {
        return String.join("\n", lines);
    }
}
