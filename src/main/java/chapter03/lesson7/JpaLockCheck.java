package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * 같은 게시글을 두 영속성 컨텍스트가 동시에 고칠 때, 나중에 커밋하는 쪽이 거절되는지 확인한다.
 *
 * JpaPost.version은 @Version이다. 애플리케이션 코드가 이 값을 직접 더하지 않는다.
 * find로 읽을 때 영속성 컨텍스트가 그 버전을 스냅샷으로 기억한다.
 * commit의 flush는 UPDATE ... SET version = 읽은값+1 WHERE id = ? AND version = 읽은값 을 보낸다.
 * 조건에 맞는 행이 1건이면 커밋되고, 0건이면 다른 트랜잭션이 그 버전을 이미 증가시킨 것이다.
 * 그때 JPA는 OptimisticLockException을 던지고 이 트랜잭션의 변경은 롤백된다.
 * 행 잠금(SELECT FOR UPDATE)은 쓰지 않는다. 읽는 동안에는 DB가 대기하지 않고, 쓰는 순간에 버전으로 충돌을 발견한다.
 *
 * EntityManager 하나가 영속성 컨텍스트 하나다. first와 second는 id가 같아도 서로 다른 객체다.
 * 한쪽 컨텍스트의 edit는 다른 쪽 메모리에 보이지 않는다. DB에 보이는 시점은 각 commit뿐이다.
 */
public class JpaLockCheck {
    public static void main(String[] args) {
        // 팩토리만 연다. 영속성 컨텍스트는 prepare와 verify가 EM을 만들 때마다 새로 생긴다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            // 회원 1명과 게시글 1건을 커밋한다. 반환 배열은 [회원 id, 게시글 id]다.
            // loginId가 unique라 같은 값으로 이 main을 다시 실행하면 1062로 준비 단계가 실패할 수 있다.
            int[] ids = JpaPracticeData.prepare(emf, "lock-check4-01", 1);
            // ids[0]은 회원이다. 낙관적 락 대상은 방금 넣은 게시글 ids[1]이다.
            verifyOptimisticLock(emf, ids[1]);
        }
    }

    private static void verifyOptimisticLock(EntityManagerFactory emf, int postId) {
        // 두 EM은 1차 캐시를 공유하지 않는다. 같은 팩토리의 커넥션만 따로 빌린다.
        // 닫는 순서는 역순이라 secondEm이 먼저 닫히고, 각 close 때 그 컨텍스트의 엔티티는 준영속이 된다.
        try (EntityManager firstEm = emf.createEntityManager();
             EntityManager secondEm = emf.createEntityManager()) {
            try {

                // 두 DB 트랜잭션을 둘 다 열어 둔다. 한 스레드에서 순서대로 실행하지만,
                // 커넥션이 둘이라 "두 사용자가 같은 글을 읽은 뒤 각자 저장"하는 간격이 된다.
                firstEm.getTransaction().begin();
                secondEm.getTransaction().begin();

                // 각 컨텍스트가 비어 있으므로 둘 다 SELECT가 나간다. first == second는 false다.
                // 각 엔티티는 자기 EM 안에서만 영속이다. 읽은 version 스냅샷도 컨텍스트마다 따로 든다.
                JpaPost first = requirePost(firstEm, postId);
                JpaPost second = requirePost(secondEm, postId);
                // 방금 INSERT된 행의 version은 0인 것이 일반적이다. 두 조회가 같은 번호를 봤는지 확인한다.
                // 여기서 이미 다르면 이후 충돌이 버전 때문인지 알 수 없다.
                long readVersion = first.getVersion();
                if (second.getVersion() != readVersion) {
                    throw new IllegalStateException("same read version required");
                }

                // edit는 각 영속 객체의 title, body만 바꾼다. SQL은 아직 없다.
                // first 컨텍스트는 "first update"가 더티하고, second 컨텍스트는 "second update"가 더티하다.
                // 서로 다른 1차 캐시라 한쪽 변경이 다른 쪽 객체를 덮어쓰지 않는다.
                first.edit("first update", "first transaction wins");
                second.edit("second update", "this transaction must fail");

                // flush가 UPDATE ... WHERE id=? AND version=readVersion 을 보내고, 1건이 갱신되면 COMMIT한다.
                // DB의 version은 readVersion+1이 된다. firstEm 스냅샷도 그 값으로 맞춰진다.
                // secondEm은 아직 자기 트랜잭션 안이고, 스냅샷 version은 예전 값 그대로다.
                firstEm.getTransaction().commit();

                try {
                    // second도 같은 WHERE version=readVersion 으로 UPDATE하려 한다.
                    // 그 버전 행은 이미 없어 갱신 건수가 0이다. commit은 OptimisticLockException을 원인으로 던지고,
                    // "second update"는 DB에 커밋되지 않는다. 예외는 PersistenceException 안에 감싸져 있을 수 있다.
                    secondEm.getTransaction().commit();
                    // 이 줄이 실행되면 버전 검사가 실패한 것이다. 충돌이 났어야 정상인 실습이다.
                    throw new IllegalStateException("optimistic lock conflict expected");
                } catch (RuntimeException e) {
                    // 실패한 commit이 트랜잭션을 이미 끝내 두었을 수 있다. 살아 있을 때만 롤백한다.
                    // 롤백 후에는 second의 메모리 값("second update")과 DB("first update")가 다르다.
                    if (secondEm.getTransaction().isActive()) {
                        secondEm.getTransaction().rollback();
                    }
                    // 바깥 예외만 보면 낙관적 락이 아닐 수 있어 원인 체인까지 확인한다.
                    // 버전 충돌이 아니면 준비 실패나 연결 오류이므로 그대로 다시 던진다.
                    if (!JpaFailureChecks.isOptimisticLock(e)) {
                        throw e;
                    }
                    System.out.println("optimisticLock=true");
                }

                // firstEm, secondEm의 1차 캐시를 믿지 않는다. second 메모리는 실패한 제목을 아직 들고 있다.
                // 새 EM의 find는 빈 캐시에서 DB를 SELECT하므로 커밋된 행만 본다.
                try (EntityManager verifyEm = emf.createEntityManager()) {
                    JpaPost saved = requirePost(verifyEm, postId);
                    // 살아남은 변경은 먼저 커밋한 first뿐이다.
                    if (!"first update".equals(saved.getTitle())) {
                        throw new IllegalStateException("unexpected final title");
                    }
                    // 성공한 UPDATE가 버전을 한 번만 올렸다. second 커밋은 번호를 증가시키지 못했다.
                    if (saved.getVersion() != readVersion + 1) {
                        throw new IllegalStateException("unexpected final version");
                    }
                    System.out.println("final=" + saved.getTitle()
                            + "|version=" + saved.getVersion());
                    // 이 두 줄은 시각 컬럼을 읽지 않는다. JpaPost에는 createdAt, updatedAt이 없고, 항상 true를 출력한다.
                    System.out.println("createdAtUnchanged=true");
                    System.out.println("updatedAtChanged=true");
                }
            } catch (RuntimeException e) {
                // 조회 실패, 첫 커밋 실패, 낙관적 락이 아닌 예외, 검증 실패가 여기로 온다.
                // 버전 충돌을 확인하고 삼킨 경로는 이 catch에 들어가지 않는다.
                // 아직 열려 있는 트랜잭션만 롤백해서, 커밋되지 않은 UPDATE가 DB에 남지 않게 한다.
                if (firstEm.getTransaction().isActive()) firstEm.getTransaction().rollback();
                if (secondEm.getTransaction().isActive()) secondEm.getTransaction().rollback();
                // 롤백 뒤 메모리의 더티 엔티티를 컨텍스트에서 떼어 낸다. 이후 flush가 그 값을 다시 쓰지 않게 한다.
                firstEm.clear();
                secondEm.clear();
                throw e;
            }
        }
    }

    private static JpaPost requirePost(EntityManager em, int postId) {
        // 이 EM의 1차 캐시에 있으면 그 영속 인스턴스를 돌려주고 SELECT는 나가지 않는다.
        // 이 실습의 find는 모두 새 컨텍스트에서 호출되므로 매번 DB를 읽는다.
        JpaPost post = em.find(JpaPost.class, postId);
        if (post == null) {
            throw new IllegalArgumentException("post not found");
        }
        return post;
    }
}
