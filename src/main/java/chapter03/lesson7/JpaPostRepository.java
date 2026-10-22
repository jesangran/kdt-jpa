package chapter03.lesson7;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

public class JpaPostRepository implements PostRepository {

    private final EntityManager em;

    public JpaPostRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(JpaPost post) {
        em.persist(post); //영속화
    }

    @Override
    public Optional<JpaPost> findById(int id) {
        return Optional.ofNullable(em.find(JpaPost.class, id));
    }

    @Override
    public List<JpaPost> findAll() {
        return em.createQuery("select p from JpaPost p order by p.id", JpaPost.class).getResultList();
    }

    @Override
    public List<JpaPost> findByTitleContaining(String keyword) {
        return em.createQuery("select p from JpaPost p where p.title like :keyword order by p.id", JpaPost.class)
                .setParameter("keyword", "%" + keyword + "%").getResultList();
    }

    @Override
    public List<JpaPost> findPage(int offset, int size) {
        if(offset < 0 || size <= 0) {
            throw new IllegalArgumentException("invalid page range");
        }

        return em.createQuery("select p from JpaPost p order by p.id", JpaPost.class)
                .setFirstResult(offset)
                .setMaxResults(size).getResultList();
    }

}
