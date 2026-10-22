package chapter03.lesson7;

import java.util.Optional;

public interface MemberRepository {

    Optional<JpaMember> findById(int id);
}
