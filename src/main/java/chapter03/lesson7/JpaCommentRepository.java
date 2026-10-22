package chapter03.lesson7;

import jakarta.persistence.EntityManager;

import java.util.List;

public class JpaCommentRepository implements CommentRepository {

    private final EntityManager em;

    public JpaCommentRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(JpaComment comment) {
        em.persist(comment);
    }

    @Override
    public List<JpaComment> findByPostId(int postId) {
        return em.createQuery(
                        "select c from JpaComment c join fetch c.post where c.post.id = :postId order by c.id",
                        JpaComment.class)
                .setParameter("postId", postId)
                .getResultList();
    }

    @Override
    public long countByPostId(int postId) {
        return em.createQuery(
                        "select count(c) from JpaComment c where c.post.id = :postId"
                        , Long.class)
                .setParameter("postId", postId)
                .getSingleResult();
    }
}