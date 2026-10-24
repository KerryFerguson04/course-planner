package planner.service;

/** A plan, semester or course that doesn't exist. The API turns this into a 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
