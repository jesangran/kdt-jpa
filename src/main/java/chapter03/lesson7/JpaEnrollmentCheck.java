package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JpaEnrollmentCheck {
    public static void main(String[] args) {
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            int studentId;
            int courseId;
            // 시작 데이터를 한 트랜잭션에서 준비하고 실제 ID를 기록합니다.
            try (EntityManager em = emf.createEntityManager()) {
                // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
                em.getTransaction().begin();
                try {
                    Student student = new Student("practice student");
                    Course course = new Course("JPA practice course");
                    em.persist(student);
                    em.persist(course);
                    // 변경 사항을 DB에 반영하고 성공한 작업을 확정합니다.
                    em.getTransaction().commit();
                    studentId = student.getId();
                    courseId = course.getId();
                } catch (RuntimeException e) {
                    // 아직 진행 중인 트랜잭션만 롤백해 실패한 변경을 취소합니다.
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    throw e;
                }
            }
            int enrollmentId;
            // 서비스가 한 요청의 조회·중복 검사·저장·커밋을 조정합니다.
            try (EntityManager em = emf.createEntityManager()) {
                EnrollmentService service = new EnrollmentService(em, new JpaEnrollmentRepository(em));
                enrollmentId = service.apply(studentId, courseId);
                try {
                    service.apply(studentId, courseId);
                    throw new IllegalStateException("duplicate rejection expected");
                } catch (IllegalStateException e) {
                    if (!"already enrolled".equals(e.getMessage())) throw e;
                    System.out.println("duplicate=" + e.getMessage());
                }
            }
            // 관리 객체가 아닌 새 조회에서 처음 신청이 보존되었는지 확인합니다.
            try (EntityManager em = emf.createEntityManager()) {
                Enrollment saved = em.find(Enrollment.class, enrollmentId);
                if (saved == null || saved.getStatus() != EnrollmentStatus.APPLIED) {
                    throw new IllegalStateException("saved enrollment missing");
                }
                System.out.println("status=" + saved.getStatus());
            }
        }
    }
}