package chapter03.lesson7;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollment_student_course",
                columnNames = {"student_id", "course_id"}))
public class Enrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.APPLIED;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Enrollment() {}

    public Enrollment(Student student, Course course) {
        if (student == null) throw new IllegalArgumentException("student required");
        if (course == null) throw new IllegalArgumentException("course required");
        // 필수 관계를 확인한 뒤 FK를 관리할 두 참조를 저장합니다.
        this.student = student;
        this.course = course;
    }

    public void approve() {
        // 이 예시는 승인으로 값을 변경해 버전 충돌을 검증합니다.
        status = EnrollmentStatus.APPROVED;
    }

    @PrePersist
    private void onCreate() {
        // 생성 시점 하나로 두 시각의 초기값을 맞춥니다.
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        // 실제 UPDATE에 사용할 수정 시각만 갱신합니다.
        updatedAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public EnrollmentStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}
