package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * 게시글 삭제가 DB에 반영되는 시점을 flush와 commit으로 구분한다.
 *
 * remove()는 DELETE SQL을 바로 보내지 않는다.
 * 영속 엔티티를 "삭제 예정(removed)"으로만 표시하고, 영속성 컨텍스트 안에 그 예약을 남겨 둔다.
 * SQL이 나가는 시점은 flush다. commit은 flush를 먼저 호출한 뒤, 그 트랜잭션을 DB에 확정한다.
 * flush만 하고 rollback하면 DELETE는 이미 실행됐어도 트랜잭션이 취소되어 행이 다시 남는다.
 *
 * 엔티티 상태 중 이 파일이 밟는 구간은 다음이다.
 * - 비영속: new JpaPost(...). 아직 컨텍스트도 DB도 모른다.
 * - 영속: persist() 또는 find() 이후. 이 EntityManager가 관리한다.
 * - 삭제 예정: 영속 엔티티에 remove()를 호출한 뒤, flush 전후. 컨텍스트는 DELETE를 보낼 대상으로 기억한다.
 * - 준영속: 트랜잭션 롤백이나 em.close() 이후. 메모리 객체는 남아도 이 컨텍스트는 더 이상 추적하지 않는다.
 *
 * 팩토리는 한 번만 만들고, 단계마다 EntityManager를 새로 연다.
 * EntityManager 하나가 영속성 컨텍스트 하나다. 이전 단계의 1차 캐시는 다음 단계로 넘어가지 않으므로
 * 3, 5단계의 find는 반드시 DB를 읽는다.
 */
public class JpaDeleteFlush {
    public static void main(String[] args) {
        // JpaSeed가 저장한 게시글의 PK. 이 글은 작성자를 빌리는 용도이고 삭제하지 않는다.
        // 시드가 다른 id를 출력했으면 그 값으로 바꾼다.
        int sourcePostId = 1;

        // persistence.xml의 "kdt"로 팩토리만 연다. 영속성 컨텍스트는 아래 EM을 만들 때마다 새로 생긴다.
        // 다섯 단계가 끝난 뒤 이 블록을 빠져나가며 팩토리가 닫히고 커넥션 풀이 반환된다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            // 1단계에서 INSERT된 실습용 게시글의 PK. 2~5단계는 이 값만 사용한다.
            int temporaryPostId;

            // 1. 기존 작성자를 재사용해 삭제 실습용 게시글을 한 건 저장한다.
            // 이 EM의 영속성 컨텍스트는 이 블록이 끝날 때 close되며 사라진다.
            try (EntityManager em = emf.createEntityManager()) {
                // RESOURCE_LOCAL 트랜잭션 시작. 이후 INSERT는 이 트랜잭션에 속한다.
                em.getTransaction().begin();
                try {
                    // 컨텍스트가 비어 있으므로 SELECT ... FROM jpa_post WHERE id = 1.
                    // 행이 있으면 JpaPost를 영속 상태로 올리고, 로드된 값의 스냅샷을 기억한다.
                    // member는 LAZY라 회원 행은 아직 읽지 않는다. member 자리에는 FK만 아는 프록시가 들어간다.
                    JpaPost source = em.find(JpaPost.class, sourcePostId);
                    if (source == null) {
                        // 시드 글이 없으면 실습용 글을 만들 작성자가 없다. 트랜잭션은 catch에서 롤백된다.
                        throw new IllegalArgumentException("source post not found");
                    }
                    // getMember()는 프록시를 반환한다. 생성자는 null만 검사하므로 여기서 회원 SELECT가 나가지 않을 수 있다.
                    // new 직후 temporary는 비영속이다. id도 아직 없다.
                    // 연관에 cascade가 없으므로 회원은 다시 INSERT되지 않고, 게시글 INSERT의 member_id에 기존 회원 PK가 들어간다.
                    JpaPost temporary = new JpaPost(source.getMember(),
                            "delete flush practice", "temporary body");
                    // persist는 비영속 객체를 이 컨텍스트의 영속 상태로 등록한다. 아직 commit 전이다.
                    // id 전략이 IDENTITY(MySQL AUTO_INCREMENT)라, PK를 알려면 INSERT가 먼저 실행되어야 한다.
                    // Hibernate는 그래서 persist 시점 또는 그 직후 flush에서 INSERT를 보낸다.
                    // 그 INSERT도 이 트랜잭션 안이므로, 이후 롤백하면 행은 사라진다. 여기서는 커밋한다.
                    em.persist(temporary);
                    // commit 전에 flush가 돌며, 아직 나가지 않은 INSERT를 DB에 보낸 뒤 COMMIT으로 확정한다.
                    // 커밋이 끝나면 다른 커넥션의 SELECT에서도 이 행이 보인다.
                    // 이 EM은 확장 영속성 컨텍스트라 commit 직후에도 temporary는 영속이다.
                    // 바로 아래 close가 컨텍스트를 닫으면서 준영속이 된다.
                    em.getTransaction().commit();
                    // IDENTITY라 commit 이후에는 DB가 부여한 id가 필드에 채워져 있다.
                    // 이후 단계는 객체가 아니라 이 숫자만 넘긴다. 객체는 이 EM이 닫히면 준영속이라 다음 EM에서 쓸 수 없다.
                    temporaryPostId = temporary.getId();
                    System.out.println("temporaryPostId=" + temporaryPostId);
                } catch (RuntimeException e) {
                    // 조회 실패나 INSERT 실패면, 트랜잭션이 살아 있을 때만 롤백해서 INSERT를 취소한다.
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    throw e;
                }
            }

            // 2. DELETE를 flush로 실행하되, 커밋하지 않고 롤백한다.
            // 새 EM이므로 1단계 컨텍스트와 1차 캐시는 없다. temporary 객체도 여기선 준영속이라 remove 대상이 될 수 없다.
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                try {
                    // 새 컨텍스트에서 PK로 다시 SELECT. 찾은 post만 이 컨텍스트의 영속 엔티티다.
                    JpaPost post = em.find(JpaPost.class, temporaryPostId);
                    if (post == null) {
                        // 1단계 커밋이 반영되지 않았으면 삭제 실습을 진행할 행이 없다.
                        throw new IllegalStateException("temporary post missing");
                    }
                    // 영속 엔티티를 삭제 예정으로 바꾼다. 이 줄에서는 DELETE SQL이 나가지 않는다.
                    // 같은 컨텍스트에서 이 id를 다시 find하면, DB에 행이 있어도 삭제 예정이기 때문에 null을 돌려준다.
                    // 준영속 객체를 remove하면 예외가 난다. find로 이 EM의 영속 객체를 얻은 뒤에만 호출한다.
                    em.remove(post);
                    // flush는 컨텍스트의 변경을 DB 트랜잭션에 반영한다. 여기서 DELETE FROM jpa_post WHERE id = ? 가 나간다.
                    // COMMIT은 하지 않는다. 트랜잭션은 여전히 열려 있고, 이 DELETE는 아직 확정이 아니다.
                    // 다른 트랜잭션의 기본 격리 수준에서는 이 삭제가 커밋 전까지 보이지 않는다.
                    em.flush(); // 이 호출이 끝나기 전에 DELETE 실행
                    // true면 rollback으로 이 DELETE를 취소할 수 있다. flush가 트랜잭션을 끝내지는 않는다는 확인이다.
                    System.out.println("afterFlushTransactionActive="
                            + em.getTransaction().isActive());
                    // 롤백은 이 트랜잭션의 DELETE를 DB가 취소하게 한다. 행은 다시 존재한다.
                    // 롤백 후 컨텍스트의 영속 엔티티는 준영속이 된다. 메모리의 post 객체와 DB가 어긋날 수 있어
                    // 이 EM으로 계속 일하면 안 된다. 블록이 끝나며 close되어 컨텍스트 자체가 버려진다.
                    em.getTransaction().rollback(); // 의도적으로 삭제를 취소
                } catch (RuntimeException e) {
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    throw e;
                }
            }

            // 3. 롤백 뒤 행이 남았는지는 방금 쓴 컨텍스트가 아니라 새 EM으로 확인한다.
            // 새 컨텍스트는 1차 캐시가 비어 있으므로 find가 DB SELECT를 보낸다.
            // 2단계 DELETE는 롤백됐으므로 행이 있어야 하고, restored는 null이 아닌 영속 엔티티다.
            // 조회만 하므로 begin/commit은 없다. 이 블록이 끝나면 이 컨텍스트도 닫힌다.
            try (EntityManager verifyEm = emf.createEntityManager()) {
                JpaPost restored = verifyEm.find(JpaPost.class, temporaryPostId);
                System.out.println("existsAfterRollback=" + (restored != null));
                if (restored == null) {
                    // flush된 DELETE가 롤백되지 않고 확정된 경우다. 실습 기대와 다르다.
                    throw new IllegalStateException("rollback verification failed");
                }
            }

            // 4. 같은 실습용 게시글을 새 컨텍스트에서 다시 조회하고, 이번에는 삭제를 커밋한다.
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();
                try {
                    // 3단계 EM은 이미 닫혔다. 여기서 find는 다시 SELECT해서 영속 post를 만든다.
                    JpaPost post = em.find(JpaPost.class, temporaryPostId);
                    if (post == null) {
                        throw new IllegalStateException("temporary post missing");
                    }
                    // 다시 삭제 예정으로만 표시한다. 이 줄만으로는 DELETE가 나가지 않는다.
                    em.remove(post);
                    // commit은 자동으로 flush한 뒤 COMMIT한다.
                    // flush가 삭제 예정을 DELETE SQL로 보내고, COMMIT이 그 삭제를 확정한다.
                    // 2단계와의 차이는 flush 뒤에 rollback이 아니라 commit이 온다는 점이다.
                    // 커밋이 끝나면 이 행은 다른 커넥션에서도 보이지 않는다. 작성자 회원 행은 삭제되지 않는다.
                    em.getTransaction().commit(); // 자동 flush로 DELETE 후 확정
                } catch (RuntimeException e) {
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    throw e;
                }
            }

            // 5. 삭제 확정도 새 컨텍스트의 SELECT로 확인한다.
            // 4단계 커밋이 반영됐으면 행이 없고 find는 null이다. null은 컨텍스트에 아무것도 올리지 않는다.
            try (EntityManager verifyEm = emf.createEntityManager()) {
                JpaPost gone = verifyEm.find(JpaPost.class, temporaryPostId);
                System.out.println("missingAfterCommit=" + (gone == null));
                if (gone != null) {
                    // 커밋되지 않았거나 다른 행을 지운 경우다.
                    throw new IllegalStateException("delete verification failed");
                }
            }
        }
    }
}