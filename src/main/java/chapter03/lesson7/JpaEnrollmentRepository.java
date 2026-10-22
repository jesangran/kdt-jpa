package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import java.util.Optional;

public class JpaEnrollmentRepository implements EnrollmentRepository {
    private final EntityManager em;

    public JpaEnrollmentRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(Enrollment enrollment) {
        // 새 신청을 영속화합니다. IDENTITY라면 이 호출에서 INSERT할 수 있습니다.
        em.persist(enrollment);
    }

    @Override
    public boolean existsByStudentIdAndCourseId(int studentId, int courseId) {
        // 학생·강좌 조합 전체를 집계해 중복 여부만 반환합니다.
        Long count = em.createQuery(
                        "select count(e) from Enrollment e "
                                + "where e.student.id = :studentId and e.course.id = :courseId",
                        Long.class)
                .setParameter("studentId", studentId)
                .setParameter("courseId", courseId)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<Enrollment> findById(int id) {
        // 결과 없음도 반환 타입으로 표현합니다.
        return Optional.ofNullable(em.find(Enrollment.class, id));
    }
    @Override
    public Optional<Student> findStudentById(int id) {
        // 연결 엔티티를 생성할 실제 학생 조회도 저장소가 담당합니다.
        return Optional.ofNullable(em.find(Student.class, id));
    }

    @Override
    public Optional<Course> findCourseById(int id) {
        // 학생·강좌 조회는 현재 실습의 수강 신청 저장소로 함께 모읍니다.
        return Optional.ofNullable(em.find(Course.class, id));
    }
}
