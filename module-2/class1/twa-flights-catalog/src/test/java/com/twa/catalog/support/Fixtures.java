package com.twa.catalog.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Fixtures {

    private Fixtures() {
        // just to avoid create instances
    }

    public static String read(String path) {
        try (InputStream input = Fixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("The fixture " + path + " does not exist");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The 86 cities of the snapshot of the monolith: code;name;time_zone;country;continent (without the header)
    public static List<String> cities() {
        return Arrays.stream(read("catalog/cities.csv").split("\n")).skip(1).map(String::trim)
                .filter(line -> !line.isEmpty()).collect(Collectors.toList());
    }
}
