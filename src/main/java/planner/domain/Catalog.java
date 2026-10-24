package planner.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All courses from catalog.json, looked up by id. Read-only once loaded. */
public final class Catalog {
    /** Layout: [ { "DEPT": { "DEPT 101": {course}, ... }, ... } ] */
    private static final TypeReference<List<Map<String, Map<String, Map<String, Object>>>>> FORMAT =
            new TypeReference<>() {};

    private final Map<String, Course> byKey = new LinkedHashMap<>();
    private final Map<String, List<String>> unlocks = new HashMap<>();

    public static Catalog load(InputStream json) throws IOException {
        Catalog catalog = new Catalog();
        for (Map<String, Map<String, Map<String, Object>>> block : new ObjectMapper().readValue(json, FORMAT)) {
            for (Map<String, Map<String, Object>> dept : block.values()) {
                for (Map<String, Object> fields : dept.values()) {
                    Course c = new Course(fields);
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

    /** "chem103" and "CHEM 103" both map to "CHEM103". */
    static String key(String id) {
        return id.replaceAll("\\s+", "").toUpperCase();
    }

    /** The course with this id, ignoring spaces and case, or null. */
    public Course get(String id) {
        return byKey.get(key(id));
    }

    public Collection<Course> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    /** Courses that list the given course in their prerequisites. */
    public List<String> unlockedBy(String id) {
        return unlocks.getOrDefault(id, List.of());
    }

    /** Matches the start of a course id (e.g. "math 2") or any part of a course name. */
    public List<Course> search(String query, int limit) {
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
