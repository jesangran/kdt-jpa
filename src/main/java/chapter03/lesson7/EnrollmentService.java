package chapter03.lesson7;

import jakarta.persistence.EntityManager;

public class EnrollmentService {
    private final EntityManager em;
    private final EnrollmentRepository enrollments;

    public EnrollmentService(EntityManager em, EnrollmentRepository enrollments) {
        this.em = em;
        this.enrollments = enrollments;
    }

    public int apply(int studentId, int courseId) {
        // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
        em.getTransaction().begin();
        try {
            // 일반적인 중복 요청은 INSERT 전에 이해하기 쉬운 오류로 거절합니다.
            if (enrollments.existsByStudentIdAndCourseId(studentId, courseId)) {
                throw new IllegalStateException("already enrolled");
            }
            // 조회 방법은 리포지토리에 위임하고 서비스는 결과 없음과 작업 순서를 조정합니다.
            Student student = enrollments.findStudentById(studentId)
                    .orElseThrow(() -> new IllegalArgumentException("student not found"));
            Course course = enrollments.findCourseById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("course not found"));

            Enrollment enrollment = new Enrollment(student, course);
            enrollments.save(enrollment);
            // 변경 사항을 DB에 반영하고 성공한 작업을 확정합니다.
            em.getTransaction().commit();
            return enrollment.getId();
        } catch (RuntimeException e) {
            // 아직 진행 중인 트랜잭션만 롤백해 실패한 변경을 취소합니다.
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            // 실패한 작업에서 관리하던 객체를 분리해 다음 작업과 섞이지 않게 합니다.
            em.clear();
            throw e;
        }
    }
}
