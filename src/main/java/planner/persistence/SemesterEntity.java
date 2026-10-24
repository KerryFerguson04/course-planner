package planner.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * One semester in a plan. Tables:
 *   semesters(id, plan_id, position, name)
 *   semester_courses(semester_id, position, course_id)
 */
@Entity
@Table(name = "semesters")
public class SemesterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "semester_courses", joinColumns = @JoinColumn(name = "semester_id"))
    @OrderColumn(name = "position")
    @Column(name = "course_id", nullable = false)
    private List<String> courseIds = new ArrayList<>();

    protected SemesterEntity() {
        // for JPA
    }

    public SemesterEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCourseIds() {
        return courseIds;
    }
}
