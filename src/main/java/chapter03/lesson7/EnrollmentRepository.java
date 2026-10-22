package chapter03.lesson7;

import java.util.Optional;

public interface EnrollmentRepository {
    void save(Enrollment enrollment);
    boolean existsByStudentIdAndCourseId(int studentId, int courseId);
    Optional<Enrollment> findById(int id);
    Optional<Student> findStudentById(int id);
    Optional<Course> findCourseById(int id);
}
