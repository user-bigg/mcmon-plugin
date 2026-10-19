package ru.mcmon.plugin.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class Utf8Files {

    private Utf8Files() {}

    static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    static void write(Path file, String text) throws IOException {
        Files.writeString(file, text);
    }
}
