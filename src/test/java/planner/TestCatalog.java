package planner;

import planner.domain.Catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the small fixture catalog in src/test/resources, shared by the unit tests. */
public final class TestCatalog {
    private static Catalog catalog;

    private TestCatalog() {}

    public static synchronized Catalog get() {
        if (catalog == null) catalog = load("/test-catalog.json");
        return catalog;
    }

    public static Catalog load(String resource) {
        try (InputStream in = TestCatalog.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing test resource " + resource);
            return Catalog.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
