package chapter03.lesson7;

import jakarta.persistence.EntityManager;

import java.util.Optional;

public class JpaMemberRepository implements MemberRepository {

    private final EntityManager em;

    public JpaMemberRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<JpaMember> findById(int id) {
        return Optional.ofNullable(em.find(JpaMember.class, id));
    }
}
