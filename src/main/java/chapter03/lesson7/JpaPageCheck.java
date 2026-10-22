package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

/**
 * findPage(offset, size) 페이징이 맞는지 검증하는 실행 클래스.
 * 글 3개를 준비한 뒤 size=1로 세 번 잘라 읽고, 잘못된 offset/size는 거절되는지 확인한다.
 */
public class JpaPageCheck {
    public static void main(String[] args) {
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            /*
             * 1) 회원 1명 + 게시글 3개를 저장한다.
             *    반환 allIds: [0]=회원id, [1]=글1, [2]=글2, [3]=글3
             */
            int[] allIds = JpaPracticeData.prepare(emf, "page-check4-01", 3);

            /* 2) 검증에 쓸 게시글 id 세 개만 뽑는다. */
            int[] postIds = {allIds[1], allIds[2], allIds[3]};

            /* 3) 페이징 조회·검증을 수행한다. */
            verifyPages(emf, postIds);
        }
    }

    private static void verifyPages(EntityManagerFactory emf, int[] preparedIds) {
        try (EntityManager em = emf.createEntityManager()) {
            PostRepository posts = new JpaPostRepository(em);

            /*
             * 4) DB에는 예전에 만든 글도 있을 수 있다.
             *    findPage는 order by id 로 "전체 게시글"을 자르므로,
             *    방금 만든 3글이 반드시 offset 0부터 시작하지 않는다.
             *    → 준비한 첫 글 id보다 작은 id인 글이 몇 개인지 세서, 그 개수를 offset으로 쓴다.
             *    예) 기존 글 3개 + 새 글 id 10,11,12 이면 earlierCount=3 → offset=3
             */
            long earlierCount = em.createQuery(
                            "select count(p) from JpaPost p where p.id < :firstId",
                            Long.class)
                    .setParameter("firstId", preparedIds[0])
                    .getSingleResult();
            int offset = Math.toIntExact(earlierCount);

            /*
             * 5) size=1 로 연속 조회한다. (한 페이지에 한 글)
             *    findPage(offset, 1)     → 준비한 1번째 글
             *    findPage(offset + 1, 1) → 준비한 2번째 글
             *    findPage(offset + 2, 1) → 준비한 3번째 글
             */
            List<JpaPost> first = posts.findPage(offset, 1);
            List<JpaPost> second = posts.findPage(offset + 1, 1);
            List<JpaPost> third = posts.findPage(offset + 2, 1);

            /*
             * 6) 각 결과가 정확히 1건이고, id가 preparedIds 순서와 같아야 한다.
             *    (중복·누락·순서 꼬임이 있으면 실패)
             */
            if (first.size() != 1 || second.size() != 1 || third.size() != 1
                    || !first.get(0).getId().equals(preparedIds[0])
                    || !second.get(0).getId().equals(preparedIds[1])
                    || !third.get(0).getId().equals(preparedIds[2])) {
                throw new IllegalStateException("unexpected page contents");
            }
            System.out.println("pageIds=" + first.get(0).getId() + ","
                    + second.get(0).getId() + "," + third.get(0).getId());

            /*
             * 7) 잘못된 offset(< 0)은 리포지토리가 IllegalArgumentException 으로 거절해야 한다.
             *    예외가 안 나면 아래 IllegalStateException 으로 테스트 실패.
             */
            try {
                posts.findPage(-1, 1);
                throw new IllegalStateException("negative offset must fail");
            } catch (IllegalArgumentException e) {
                System.out.println("invalidOffset=" + e.getMessage());
            }

            /* 8) 잘못된 size(<= 0)도 마찬가지로 거절해야 한다. */
            try {
                posts.findPage(0, 0);
                throw new IllegalStateException("zero size must fail");
            } catch (IllegalArgumentException e) {
                System.out.println("invalidSize=" + e.getMessage());
            }
        }
    }
}
