package chapter03.lesson7;

import jakarta.persistence.*;

@Entity
@Table(name = "student")
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    protected Student() {}

    public Student(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("student name required");
        // 매핑 길이는 Java 입력 검증을 대신하지 않으므로 저장 전에 검사합니다.
        if (name.length() > 50) throw new IllegalArgumentException("student name too long");
        this.name = name;
    }

    public Integer getId() { return id; }
}