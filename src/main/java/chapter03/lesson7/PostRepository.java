package chapter03.lesson7;

import java.util.List;
import java.util.Optional;

public interface PostRepository {

    void save(JpaPost post);

    Optional<JpaPost> findById(int id); //id가 있을 수도 있고, 없을 수도 있다.

    List<JpaPost> findAll();

    List<JpaPost> findByTitleContaining(String keyword);

    List<JpaPost> findPage(int offset, int size);

}
