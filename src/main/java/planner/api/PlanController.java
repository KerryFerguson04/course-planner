package planner.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import planner.dto.Dtos.NameRequest;
import planner.dto.Dtos.OptionalNameRequest;
import planner.dto.Dtos.PlanSummary;
import planner.dto.Dtos.PlanView;
import planner.service.PlanService;

import java.net.URI;
import java.util.List;

/**
 * Plans, their semesters, and the courses in them. Every change returns the
 * whole updated plan, with each course's status re-checked.
 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {
    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<PlanSummary> list() {
        return plans.list();
    }

    @PostMapping
    public ResponseEntity<PlanView> create(@Valid @RequestBody(required = false) OptionalNameRequest body) {
        PlanView plan = plans.create(body == null ? null : body.name());
        return ResponseEntity.created(URI.create("/api/plans/" + plan.id())).body(plan);
    }

    @GetMapping("/{planId}")
    public PlanView get(@PathVariable long planId) {
        return plans.get(planId);
    }

    @PatchMapping("/{planId}")
    public PlanView rename(@PathVariable long planId, @Valid @RequestBody NameRequest body) {
        return plans.rename(planId, body.name());
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@PathVariable long planId) {
        plans.delete(planId);
        return ResponseEntity.noContent().build();
    }

    // ---- Semesters ----

    @PostMapping("/{planId}/semesters")
    public ResponseEntity<PlanView> addSemester(@PathVariable long planId,
                                                @Valid @RequestBody(required = false) OptionalNameRequest body) {
        PlanView plan = plans.addSemester(planId, body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PatchMapping("/{planId}/semesters/{semesterId}")
    public PlanView renameSemester(@PathVariable long planId, @PathVariable long semesterId,
                                   @Valid @RequestBody NameRequest body) {
        return plans.renameSemester(planId, semesterId, body.name());
    }

    @DeleteMapping("/{planId}/semesters/{semesterId}")
    public PlanView deleteSemester(@PathVariable long planId, @PathVariable long semesterId) {
        return plans.deleteSemester(planId, semesterId);
    }

    // ---- Courses ----

    /** Adds the course to this semester, moving it if it's elsewhere in the plan. Idempotent. */
    @PutMapping("/{planId}/semesters/{semesterId}/courses/{courseId}")
    public PlanView placeCourse(@PathVariable long planId, @PathVariable long semesterId,
                                @PathVariable String courseId) {
        return plans.placeCourse(planId, semesterId, courseId);
    }

    @DeleteMapping("/{planId}/semesters/{semesterId}/courses/{courseId}")
    public PlanView removeCourse(@PathVariable long planId, @PathVariable long semesterId,
                                 @PathVariable String courseId) {
        return plans.removeCourse(planId, semesterId, courseId);
    }

    @PutMapping("/{planId}/completed/{courseId}")
    public PlanView markCompleted(@PathVariable long planId, @PathVariable String courseId) {
        return plans.markCompleted(planId, courseId);
    }

    @DeleteMapping("/{planId}/completed/{courseId}")
    public PlanView unmarkCompleted(@PathVariable long planId, @PathVariable String courseId) {
        return plans.unmarkCompleted(planId, courseId);
    }
}
