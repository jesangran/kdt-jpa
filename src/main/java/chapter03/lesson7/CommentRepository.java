package chapter03.lesson7;

import java.util.List;

public interface CommentRepository {
    void save(JpaComment comment);

    List<JpaComment> findByPostId(int postId);

    long countByPostId(int postId);
}