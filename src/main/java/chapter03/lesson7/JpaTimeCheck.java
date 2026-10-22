package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.time.LocalDateTime;

public class JpaTimeCheck {
    public static void main(String[] args) {
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            int[] ids = JpaPracticeData.prepare(emf, "time-check4-01", 1);
            verifyTimestamps(emf, ids[1]);
        }
    }

    private static void verifyTimestamps(EntityManagerFactory emf, int postId) {
        LocalDateTime createdBefore;
        LocalDateTime updatedBefore;

        // 1. 수정 전 시각을 새 조회로 기록합니다.
        try (EntityManager em = emf.createEntityManager()) {
            JpaPost post = requirePost(em, postId);
            createdBefore = post.getCreatedAt();
            updatedBefore = post.getUpdatedAt();
        }

        // 2. 별도 트랜잭션에서 실제 필드 값을 바꿉니다.
        try (EntityManager em = emf.createEntityManager()) {
            // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
            em.getTransaction().begin();
            try {
                JpaPost post = requirePost(em, postId);
                post.edit("page post edited", "changed body");
                // 변경 사항을 DB에 반영하고 성공한 작업을 확정합니다.
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                // 아직 진행 중인 트랜잭션만 롤백해 실패한 변경을 취소합니다.
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }

        LocalDateTime updatedAfterChange;
        try (EntityManager em = emf.createEntityManager()) {
            JpaPost saved = requirePost(em, postId);
            updatedAfterChange = saved.getUpdatedAt();
            if (!createdBefore.equals(saved.getCreatedAt())
                    || !updatedAfterChange.isAfter(updatedBefore)) {
                throw new IllegalStateException("timestamp change verification failed");
            }
            System.out.println("createdUnchanged="
                    + createdBefore.equals(saved.getCreatedAt()));
            System.out.println("updatedChanged="
                    + updatedAfterChange.isAfter(updatedBefore));
        }

        // 3. 값을 변경하지 않고 조회만 한 트랜잭션을 커밋합니다.
        try (EntityManager em = emf.createEntityManager()) {
            // 트랜잭션을 시작해 조회와 변경을 하나의 작업으로 묶습니다.
            em.getTransaction().begin();
            try {
                // 조회만 수행해 실제 변경이 없는 커밋을 검증합니다.
                requirePost(em, postId);
                // 변경 사항을 DB에 반영하고 성공한 작업을 확정합니다.
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                // 검증 중 오류가 나도 활성 트랜잭션을 남기지 않습니다.
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }

        // 4. UPDATE가 없었다면 updatedAt도 다시 바뀌지 않습니다.
        try (EntityManager em = emf.createEntityManager()) {
            JpaPost saved = requirePost(em, postId);
            if (!updatedAfterChange.equals(saved.getUpdatedAt())) {
                throw new IllegalStateException("unchanged transaction changed time");
            }
            System.out.println("unchangedTransactionKeptTime="
                    + updatedAfterChange.equals(saved.getUpdatedAt()));
        }
    }

    private static JpaPost requirePost(EntityManager em, int postId) {
        JpaPost post = em.find(JpaPost.class, postId);
        if (post == null) throw new IllegalArgumentException("post not found");
        return post;
    }
}
