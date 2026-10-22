package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * persist로 IDENTITY id를 받은 뒤 rollback 하면 DB 행은 취소되지만,
 * 자바 객체의 id 필드는 그대로 남는 것을 확인한다.
 *
 * IDENTITY는 persist 시점에 INSERT가 나가면서 DB가 준 번호가 객체에 채워진다.
 * rollback은 그 INSERT를 없던 일로 만든다. 객체 필드까지 지워 주지는 않는다.
 *
 * 같은 EntityManager의 find로는 "방금 롤백한 메모리 객체"와 헷갈리기 쉽다.
 * DB에 행이 남았는지는 롤백에 쓰지 않은 새 EntityManager의 find로 확인한다.
 * commit()은 호출하지 않는다.
 */
public class JpaRollbackCheck {
    public static void main(String[] args) {

        // 팩토리는 한 번만 연다. 아래 두 EntityManager가 같은 유닛 "kdt"를 쓴다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {

            // 첫 EM이 닫힌 뒤에도 두 번째 EM에서 쓸 수 있게 바깥에 둔다.
            Integer assignedId;

            // TODO 1: 첫 번째 EntityManager에서 트랜잭션을 시작하고 회원을 저장한 뒤
            //         assignedId= 형식으로 할당된 ID를 출력합니다.
            try (EntityManager em = emf.createEntityManager()) {
                em.getTransaction().begin();

                try {
                    // 비영속. 아직 DB와 무관. id는 null.
                    JpaMember member = new JpaMember("mission-01-rollback", "rollbackUser");
                    // IDENTITY라 persist 때 INSERT가 나가고 id가 채워진다 → 영속.
                    // 이 순간 DB에는 임시로 행이 생기지만, 커밋 전이라 다른 트랜잭션에는 안 보이거나 롤백 대상이다.
                    em.persist(member);

                    assignedId = member.getId();
                    System.out.println("assignedId=" + assignedId);

                    // TODO 2: 트랜잭션을 롤백하고 idAfterRollback= 형식으로 객체의 ID를 다시 출력합니다.
                    // rollback: 이번 트랜잭션의 INSERT를 취소. DB에서 그 행은 없어진다.
                    // member.getId()는 자바 필드라서 롤백해도 null로 돌아가지 않는다.
                    em.getTransaction().rollback();
                    System.out.println("idAfterRollback=" + member.getId());
                } catch (RuntimeException e) {
                    // begin 이후 예외면 살아 있는 트랜잭션을 닫는다.
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    throw e;
                }
                // 이 try가 끝나면 em.close(). 첫 컨텍스트는 사라진다.
            }

            // TODO 3: 새 EntityManager로 같은 ID의 회원을 조회하고
            //         existsAfterRollback= 형식으로 존재 여부를 출력합니다.
            // 새 1차 캐시로 DB를 다시 본다. 롤백이 됐다면 find 결과는 null.
            try (EntityManager em = emf.createEntityManager()) {
                JpaMember found = em.find(JpaMember.class, assignedId);
                System.out.println("existsAfterRollback=" + (found != null));
            }
        }
    }
}
