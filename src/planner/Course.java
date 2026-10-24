package planner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One catalog course. */
final class Course {
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    final String id;
    final String name;
    final String descr;
    final String credits;
    final String preReqText;
    final String restrict;
    final String typ;
    final double minCredits;
    final double maxCredits;
    /** Terms the course is normally offered, e.g. "Fall", "Spring". Empty if unknown. */
    final Set<String> terms = new LinkedHashSet<>();
    Prereq prereq = Prereq.NONE;

    Course(Map<String, Object> json) {
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

        for (String t : typ.split(",")) {
            if (!t.isBlank()) terms.add(t.trim());
        }
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
