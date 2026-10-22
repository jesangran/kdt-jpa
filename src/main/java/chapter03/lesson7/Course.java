package chapter03.lesson7;

import jakarta.persistence.*;

@Entity
@Table(name = "course")
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String title;

    protected Course() {}

    public Course(String title) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("course title required");
        // DB 열 길이와 같은 상한을 Java에서도 명시합니다.
        if (title.length() > 100) throw new IllegalArgumentException("course title too long");
        this.title = title;
    }

    public Integer getId() { return id; }
}
