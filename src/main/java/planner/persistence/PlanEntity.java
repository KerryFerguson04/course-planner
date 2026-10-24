package planner.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A saved plan. Tables:
 *   plans(id, name, updated_at)
 *   plan_completed(plan_id, position, course_id)
 *   semesters(id, plan_id, position, name)  — see {@link SemesterEntity}
 */
@Entity
@Table(name = "plans")
public class PlanEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @ElementCollection
    @CollectionTable(name = "plan_completed", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    @Column(name = "course_id", nullable = false)
    private List<String> completed = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "plan_id")
    @OrderColumn(name = "position")
    private List<SemesterEntity> semesters = new ArrayList<>();

    protected PlanEntity() {
        // for JPA
    }

    public PlanEntity(String name) {
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Called by the service after every change, since collection edits don't trigger @PreUpdate. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public List<String> getCompleted() {
        return completed;
    }

    public List<SemesterEntity> getSemesters() {
        return semesters;
    }
}
