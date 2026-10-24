package planner.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One catalog course. */
public final class Course {
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    public final String id;
    public final String name;
    public final String descr;
    public final String credits;
    public final String preReqText;
    public final String restrict;
    public final String typ;
    public final double minCredits;
    public final double maxCredits;
    /** Terms the course is normally offered, e.g. "Fall", "Spring". Empty if unknown. */
    public final Set<String> terms;
    Prereq prereq = Prereq.NONE;

    public Course(Map<String, Object> json) {
        id = str(json, "id");
        name = str(json, "name");
        descr = str(json, "descr");
        credits = str(json, "credits");
        preReqText = str(json, "preReq");
        restrict = str(json, "restrict");
        typ = str(json, "typ");

        List<Double> nums = new ArrayList<>();
        Matcher m = NUMBER.matcher(credits);
        while (m.find()) nums.add(Double.parseDouble(m.group()));
        minCredits = nums.isEmpty() ? 0 : nums.get(0);
        maxCredits = nums.isEmpty() ? 0 : nums.get(nums.size() - 1);

        Set<String> t = new LinkedHashSet<>();
        for (String term : typ.split(",")) {
            if (!term.isBlank()) t.add(term.trim());
        }
        terms = Collections.unmodifiableSet(t);
    }

    public Prereq prereq() {
        return prereq;
    }

    private static String str(Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v == null ? "" : v.toString().trim();
    }

    @Override
    public String toString() {
        return id + " — " + name;
    }
}
