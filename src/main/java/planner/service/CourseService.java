package planner.service;

import org.springframework.stereotype.Service;
import planner.domain.Catalog;
import planner.domain.Course;
import planner.domain.Plan;
import planner.dto.Dtos.CourseDetail;
import planner.dto.Dtos.CourseResult;
import planner.dto.Dtos.PrereqView;

import java.util.ArrayList;
import java.util.List;

/** Catalog search and course details, optionally with badges for a plan. */
@Service
public class CourseService {
    public static final int MAX_RESULTS = 300;

    private final Catalog catalog;
    private final PlanService plans;

    public CourseService(Catalog catalog, PlanService plans) {
        this.catalog = catalog;
        this.plans = plans;
    }

    public List<CourseResult> search(String query, int limit, Long planId) {
        Plan plan = planId == null ? null : plans.loadDomain(planId);
        int capped = Math.max(1, Math.min(limit, MAX_RESULTS));
        return catalog.search(query, capped).stream().map(c -> result(c, plan)).toList();
    }

    public CourseDetail get(String courseId) {
        Course c = catalog.get(courseId);
        if (c == null) throw new NotFoundException("Course " + courseId + " not found");
        PrereqView prereqs = new PrereqView(c.preReqText, c.prereq().describe(), c.prereq().groups(),
                c.prereq().notes(), c.prereq().concurrentOk());
        return new CourseDetail(c.id, c.name, c.credits, new ArrayList<>(c.terms), c.descr, c.restrict,
                prereqs, catalog.unlockedBy(c.id));
    }

    private CourseResult result(Course c, Plan plan) {
        if (plan == null) return new CourseResult(c.id, c.name, c.credits, null, null);
        Plan.Availability a = plan.availability(catalog, c.id);
        String hint = switch (a) {
            case IN_PLAN -> "Already in your plan";
            case READY -> "Your plan covers the prerequisites";
            case CHECK -> "Some prerequisites can't be checked automatically";
            case LOCKED -> "Needs " + String.join(" and ", plan.missing(catalog, c.id));
        };
        return new CourseResult(c.id, c.name, c.credits, a.name(), hint);
    }
}
