package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JpaRelationCheck {
    public static void main(String[] args) {

        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            // 재실행할 때 relation-check4-01만 relation-check4-02처럼 미사용 값으로 바꿉니다.
            int[] ids = JpaPracticeData.prepare(emf, "relation-check4-01", 1);
            int memberId = ids[0];
            int postId = ids[1];

            // 비교할 기존 댓글 한 건을 먼저 준비하고 이후 제거 대상과 ID를 구분합니다.
            int existingCommentId = addComment(emf, postId, memberId);
            long otherCountBefore = countComments(emf, postId);
            int commentId = addComment(emf, postId, memberId);

            // 새 조회에서 저장된 FK와 회원 참조를 확인해 Java 객체만 보고 판단하지 않습니다.
            try (EntityManager verifyEm = emf.createEntityManager()) {
                JpaComment saved = verifyEm.find(JpaComment.class, commentId);
                if (saved == null || !saved.getPost().getId().equals(postId)
                        || !saved.getMember().getId().equals(memberId)) {
                    throw new IllegalStateException("saved relation mismatch");
                }
                System.out.println("savedFkPostId=" + saved.getPost().getId());
            }

            // 지역 변수의 참조만 제거해도 엔티티 관계와 DB 행은 바뀌지 않습니다.
            JpaComment local = findComment(emf, commentId);
            local = null;
            System.out.println("existsAfterLocalNull="
                    + (findComment(emf, commentId) != null));

            removeFromRelation(emf, postId, commentId);
            if (findComment(emf, existingCommentId) == null) {
                throw new IllegalStateException("existing comment must remain");
            }

            // 새 조회에서 대상 댓글만 삭제되고 기존 댓글 수는 유지되는지 확인합니다.
            System.out.println("removedExists="
                    + (findComment(emf, commentId) != null));
            System.out.println("otherCountUnchanged="
                    + (countComments(emf, postId) == otherCountBefore));
        }
    }

    private static int addComment(EntityManagerFactory emf,
                                  int postId,
                                  int memberId) {
        try (EntityManager em = emf.createEntityManager()) {
            // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
            em.getTransaction().begin();

            try {
                JpaPost post = requirePost(em, postId);
                JpaMember member = em.find(JpaMember.class, memberId);
                if (member == null) throw new IllegalArgumentException("member not found");

                // 생성자가 관계의 주인인 JpaComment.post를 설정합니다.
                JpaComment comment = new JpaComment(post, member, "relation assignment");

                // 편의 메서드가 반대 방향 컬렉션도 같은 관계로 맞춥니다.
                int sizeBefore = post.getComments().size();
                post.addComment(comment);

                if (post.getComments().size() != sizeBefore + 1) {
                    throw new IllegalStateException("collection must increase by one");
                }
                System.out.println("collectionSizeAfterAdd=" + post.getComments().size());

                // cascade가 새 댓글에 persist 작업을 전파합니다.
                em.getTransaction().commit();
                int commentId = comment.getId();
                System.out.println("fkPostId=" + comment.getPost().getId());

                return commentId;
            } catch (RuntimeException e) {
                // 아직 진행 중인 트랜잭션만 롤백해 실패한 변경을 취소합니다.
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
    }

    private static void removeFromRelation(EntityManagerFactory emf,
                                           int postId,
                                           int commentId) {
        try (EntityManager em = emf.createEntityManager()) {
            // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
            em.getTransaction().begin();

            try {
                JpaPost post = requirePost(em, postId);
                JpaComment target = em.find(JpaComment.class, commentId);

                if (target == null) throw new IllegalArgumentException("comment not found");
                // 관리 중인 부모 컬렉션에서 제거하면 orphan removal이 DELETE를 예약합니다.
                post.removeComment(target);
                // 변경 사항을 DB에 반영하고 성공한 작업을 확정합니다.
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                // 아직 진행 중인 트랜잭션만 롤백해 실패한 변경을 취소합니다.
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
    }

    private static JpaPost requirePost(EntityManager em, int postId) {
        JpaPost post = em.find(JpaPost.class, postId);

        if (post == null) throw new IllegalArgumentException("post not found");
        return post;
    }

    private static JpaComment findComment(EntityManagerFactory emf, int commentId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.find(JpaComment.class, commentId);
        }
    }

    private static long countComments(EntityManagerFactory emf, int postId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            "select count(c) from JpaComment c where c.post.id = :postId",
                            Long.class)
                    .setParameter("postId", postId)
                    .getSingleResult();
        }
    }
}
