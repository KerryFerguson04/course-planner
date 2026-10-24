package planner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All courses from catalog.json, looked up by id. */
final class Catalog {
    private final Map<String, Course> byKey = new LinkedHashMap<>();
    private final Map<String, List<String>> unlocks = new HashMap<>();

    static Catalog load(Path path) throws IOException {
        Object root = JsonParser.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        Catalog catalog = new Catalog();

        // Layout: [ { "DEPT": { "DEPT 101": {course}, ... }, ... } ]
        List<?> blocks = root instanceof List<?> list ? list : List.of(root);
        for (Object block : blocks) {
            for (Object dept : asMap(block).values()) {
                for (Object json : asMap(dept).values()) {
                    Course c = new Course(asMap(json));
                    if (!c.id.isEmpty()) catalog.byKey.putIfAbsent(key(c.id), c);
                }
            }
        }

        // Prereqs are parsed after loading so they can be checked against every course id.
        for (Course c : catalog.byKey.values()) {
            c.prereq = PrereqParser.parse(c.preReqText, catalog, c.id);
            for (Prereq.Group g : c.prereq.groups()) {
                for (String id : g.anyOf()) catalog.unlocks.computeIfAbsent(id, k -> new ArrayList<>()).add(c.id);
            }
        }
        return catalog;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("Unexpected catalog format");
        return (Map<String, Object>) o;
    }

    /** "chem103" and "CHEM 103" both map to "CHEM103". */
    static String key(String id) {
        return id.replaceAll("\\s+", "").toUpperCase();
    }

    Course get(String id) {
        return byKey.get(key(id));
    }

    Collection<Course> all() {
        return byKey.values();
    }

    /** Courses that list the given course in their prerequisites. */
    List<String> unlockedBy(String id) {
        return unlocks.getOrDefault(id, List.of());
    }

    /** Matches the start of a course id (e.g. "math 2") or any part of a course name. */
    List<Course> search(String query, int limit) {
        String q = query.trim().toLowerCase();
        String qKey = key(query);
        List<Course> out = new ArrayList<>();
        for (Course c : byKey.values()) {
            if (q.isEmpty() || key(c.id).startsWith(qKey) || c.name.toLowerCase().contains(q)) {
                out.add(c);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }
}
