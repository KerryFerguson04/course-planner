package planner.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import planner.dto.Dtos.CourseDetail;
import planner.dto.Dtos.CourseResult;
import planner.service.CourseService;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    /** GET /api/courses?q=acct&limit=50&planId=1 — planId adds Ready/Locked badges. */
    @GetMapping
    public List<CourseResult> search(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "50") int limit,
                                     @RequestParam(required = false) Long planId) {
        return courses.search(q, limit, planId);
    }

    /** GET /api/courses/ACCT%20207 (or /api/courses/acct207). */
    @GetMapping("/{courseId}")
    public CourseDetail get(@PathVariable String courseId) {
        return courses.get(courseId);
    }
}
