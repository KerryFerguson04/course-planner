package planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import planner.domain.Prereq;

import java.time.Instant;
import java.util.List;

/**
 * JSON shapes the API sends and receives. Kept separate from the database
 * entities so the API can change without changing the tables, and vice versa.
 */
public final class Dtos {
    private Dtos() {}

    // ---- Requests ----

    /** Body for renaming a plan or semester. */
    public record NameRequest(@NotBlank @Size(max = 80) String name) {}

    /** Body for creating a plan or semester; a missing or blank name gets a default. */
    public record OptionalNameRequest(@Size(max = 80) String name) {}

    // ---- Courses ----

    /**
     * One search result. {@code availability} and {@code hint} are only
     * present when the search was made for a plan.
     */
    public record CourseResult(String id, String name, String credits, String availability, String hint) {}

    public record CourseDetail(String id, String name, String credits, List<String> offered, String description,
                               String restrictions, PrereqView prerequisites, List<String> prerequisiteFor) {}

    public record PrereqView(String text, String summary, List<Prereq.Group> groups, List<String> notes,
                             boolean concurrentOk) {}

    // ---- Plans ----

    public record PlanSummary(long id, String name, Instant updatedAt) {}

    public record PlanView(long id, String name, Instant updatedAt, String completedCredits,
                           List<CourseCard> completed, List<SemesterView> semesters) {}

    public record SemesterView(long id, String name, String credits, List<CourseCard> courses) {}

    /** {@code status} is OK, WARN or ERROR for semester courses, DONE for completed ones. */
    public record CourseCard(String id, String name, String credits, String status, List<String> messages) {}
}
