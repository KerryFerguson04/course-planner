package planner.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import planner.domain.Catalog;
import planner.domain.Course;
import planner.domain.Plan;
import planner.dto.Dtos.CourseCard;
import planner.dto.Dtos.PlanSummary;
import planner.dto.Dtos.PlanView;
import planner.dto.Dtos.SemesterView;
import planner.persistence.PlanEntity;
import planner.persistence.PlanRepository;
import planner.persistence.SemesterEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan use cases. Each public method runs in one transaction: load the plan
 * entity, change it, save it, and return a fresh {@link PlanView} with every
 * course re-checked, so the client never has to compute statuses itself.
 *
 * A course appears at most once in a plan: placing it somewhere moves it.
 */
@Service
@Transactional
public class PlanService {
    private static final String DEFAULT_PLAN_NAME = "My plan";

    private final PlanRepository plans;
    private final Catalog catalog;

    public PlanService(PlanRepository plans, Catalog catalog) {
        this.plans = plans;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list() {
        return plans.findAllByOrderByUpdatedAtDesc().stream()
                .map(p -> new PlanSummary(p.getId(), p.getName(), p.getUpdatedAt()))
                .toList();
    }

    public PlanView create(String name) {
        PlanEntity plan = new PlanEntity(orDefault(name, DEFAULT_PLAN_NAME));
        plan.getSemesters().add(new SemesterEntity(Plan.nextTermName(null)));
        return save(plan);
    }

    @Transactional(readOnly = true)
    public PlanView get(long planId) {
        return view(find(planId));
    }

    /** The plan as the domain model, for checks outside this service (e.g. search badges). */
    @Transactional(readOnly = true)
    public Plan loadDomain(long planId) {
        return toDomain(find(planId));
    }

    public PlanView rename(long planId, String name) {
        PlanEntity plan = find(planId);
        plan.setName(name.trim());
        return save(plan);
    }

    public void delete(long planId) {
        plans.delete(find(planId));
    }

    public PlanView addSemester(long planId, String name) {
        PlanEntity plan = find(planId);
        List<SemesterEntity> semesters = plan.getSemesters();
        String suggested = Plan.nextTermName(semesters.isEmpty() ? null : semesters.get(semesters.size() - 1).getName());
        if (suggested.isEmpty()) suggested = "Semester " + (semesters.size() + 1);
        semesters.add(new SemesterEntity(orDefault(name, suggested)));
        return save(plan);
    }

    public PlanView renameSemester(long planId, long semesterId, String name) {
        PlanEntity plan = find(planId);
        findSemester(plan, semesterId).setName(name.trim());
        return save(plan);
    }

    public PlanView deleteSemester(long planId, long semesterId) {
        PlanEntity plan = find(planId);
        plan.getSemesters().remove(findSemester(plan, semesterId));
        return save(plan);
    }

    /** Puts a course in a semester, moving it from wherever else it is in the plan. */
    public PlanView placeCourse(long planId, long semesterId, String courseId) {
        PlanEntity plan = find(planId);
        SemesterEntity target = findSemester(plan, semesterId);
        String id = requireCourse(courseId);
        removeEverywhere(plan, id);
        target.getCourseIds().add(id);
        return save(plan);
    }

    public PlanView removeCourse(long planId, long semesterId, String courseId) {
        PlanEntity plan = find(planId);
        SemesterEntity semester = findSemester(plan, semesterId);
        String id = requireCourse(courseId);
        if (!semester.getCourseIds().remove(id)) {
            throw new NotFoundException(id + " is not in " + semester.getName());
        }
        return save(plan);
    }

    /** Marks a course completed, moving it out of any semester. */
    public PlanView markCompleted(long planId, String courseId) {
        PlanEntity plan = find(planId);
        String id = requireCourse(courseId);
        removeEverywhere(plan, id);
        plan.getCompleted().add(id);
        return save(plan);
    }

    public PlanView unmarkCompleted(long planId, String courseId) {
        PlanEntity plan = find(planId);
        String id = requireCourse(courseId);
        if (!plan.getCompleted().remove(id)) throw new NotFoundException(id + " is not marked completed");
        return save(plan);
    }

    // ---- Helpers ----

    private PlanEntity find(long planId) {
        return plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan " + planId + " not found"));
    }

    private static SemesterEntity findSemester(PlanEntity plan, long semesterId) {
        return plan.getSemesters().stream()
                .filter(s -> s.getId() == semesterId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Semester " + semesterId + " not found in plan " + plan.getId()));
    }

    /** The catalog's spelling of a course id ("acct207" -> "ACCT 207"), or 404. */
    private String requireCourse(String courseId) {
        Course c = catalog.get(courseId);
        if (c == null) throw new NotFoundException("Course " + courseId + " not found");
        return c.id;
    }

    private static void removeEverywhere(PlanEntity plan, String courseId) {
        plan.getCompleted().remove(courseId);
        for (SemesterEntity s : plan.getSemesters()) s.getCourseIds().remove(courseId);
    }

    private static String orDefault(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }

    /** Saves and flushes so new rows have ids before the view is built. */
    private PlanView save(PlanEntity plan) {
        plan.touch();
        return view(plans.saveAndFlush(plan));
    }

    private static Plan toDomain(PlanEntity entity) {
        Plan plan = new Plan();
        plan.completed.addAll(entity.getCompleted());
        for (SemesterEntity s : entity.getSemesters()) {
            Plan.Semester semester = new Plan.Semester(s.getName());
            semester.courses.addAll(s.getCourseIds());
            plan.semesters.add(semester);
        }
        return plan;
    }

    private PlanView view(PlanEntity entity) {
        Plan plan = toDomain(entity);

        List<CourseCard> completed = new ArrayList<>();
        for (String id : plan.completed) completed.add(card(id, "DONE", List.of()));

        List<SemesterView> semesters = new ArrayList<>();
        for (int i = 0; i < plan.semesters.size(); i++) {
            Plan.Semester s = plan.semesters.get(i);
            List<CourseCard> cards = new ArrayList<>();
            for (String id : s.courses) {
                Plan.Check check = plan.check(catalog, i, id);
                cards.add(card(id, check.status().name(), check.messages()));
            }
            semesters.add(new SemesterView(entity.getSemesters().get(i).getId(), s.name,
                    Plan.creditTotal(catalog, s.courses), cards));
        }

        return new PlanView(entity.getId(), entity.getName(), entity.getUpdatedAt(),
                Plan.creditTotal(catalog, plan.completed), completed, semesters);
    }

    private CourseCard card(String id, String status, List<String> messages) {
        Course c = catalog.get(id);
        return new CourseCard(id, c == null ? "Not in catalog" : c.name, c == null ? "" : c.credits, status, messages);
    }
}
