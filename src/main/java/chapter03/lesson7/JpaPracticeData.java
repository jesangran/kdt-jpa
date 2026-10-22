package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public class JpaPracticeData {
    public static int[] prepare(EntityManagerFactory emf, String loginId, int postCount) {
        if (postCount <= 0) throw new IllegalArgumentException("post count required");

        try (EntityManager em = emf.createEntityManager()) {
            // 회원과 게시글을 함께 저장해 FK가 참조할 회원을 먼저 준비합니다.
            em.getTransaction().begin();

            try {
                JpaMember member = new JpaMember(loginId, "practice writer");
                em.persist(member);

                int[] ids = new int[postCount + 1];
                ids[0] = member.getId();

                for (int i = 0; i < postCount; i++) {
                    JpaPost post = new JpaPost(member, "practice post " + i, "practice body");
                    // 준비 단계에서는 댓글 실습의 시작 조건인 공개 상태를 만듭니다.
                    post.publish();
                    em.persist(post);
                    ids[i + 1] = post.getId();
                }
                // IDENTITY로 얻은 실제 ID도 커밋 성공 뒤에만 호출자에게 전달합니다.
                em.getTransaction().commit();
                return ids;
            } catch (RuntimeException e) {
                // 회원이나 게시글 저장 중 하나라도 실패하면 준비 작업 전체를 취소합니다.
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
    }
}
