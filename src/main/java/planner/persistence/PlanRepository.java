package planner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<PlanEntity, Long> {
    List<PlanEntity> findAllByOrderByUpdatedAtDesc();
}
