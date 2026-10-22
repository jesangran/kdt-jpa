package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * 이미 저장된 JpaPost 한 건을 조회한 뒤 제목과 본문을 고친다.
 * em.update() 같은 메서드는 없다. 영속성 컨텍스트가 관리 중인 객체의 필드가
 * 바뀌면, commit 직전 flush 때 그 차이를 보고 UPDATE SQL을 만든다. 이것을 dirty checking이라고 한다.
 *
 * 엔티티는 대략 네 가지 상태에 있다.
 * - 비영속(new): new JpaPost(...) 직후. 영속성 컨텍스트도, DB도 이 객체를 모른다.
 * - 영속(managed): 이 EntityManager의 영속성 컨텍스트가 관리 중. find로 조회됐거나 persist된 상태.
 * - 준영속(detached): 한때는 영속이었지만 이제 컨텍스트 밖. close, clear, 롤백 이후.
 * - 삭제(removed): remove() 호출 후, flush/commit 때 DELETE가 나갈 대상. 이 클래스에서는 쓰지 않는다.
 *
 * 영속성 컨텍스트는 EntityManager 안에 있는 1차 캐시다.
 * 같은 EntityManager에서 같은 클래스, 같은 PK를 다시 찾으면 DB를 다시 치지 않고
 * 이미 올려 둔 그 인스턴스를 돌려준다. 그래서 같은 EM 안에서는 같은 id의 엔티티가 항상 같은 객체다.
 *
 * 이 main은 emf.createEntityManager()로 만든 애플리케이션 관리 EntityManager다.
 * 이 방식의 영속성 컨텍스트는 EM을 만들 때 생기고 em.close() 할 때까지 유지된다(확장 영속성 컨텍스트).
 * commit이 끝나도 post는 바로 준영속이 되지 않고, EM이 살아있는 동안 계속 영속이다.
 * 스프링 @PersistenceContext 기본값(트랜잭션 범위)은 commit과 함께 준영속이 되므로, 그 그림과 헷갈리면 안 된다.
 */
public class JpaPostEdit {
    public static void main(String[] args) {
        int postId = 1;

        // persistence.xml의 unit 이름 "kdt"로 팩토리를 만든다.
        // 엔티티 매핑을 읽고, DB 방언과 커넥션 풀을 준비하는 무거운 작업이다.
        // 실제 앱에서는 프로세스가 켜질 때 한 번만 만들고, 끝날 때 한 번만 닫는다.
        // 여기서는 예제 main이라 실행이 끝나면 바로 닫히게 try-with-resources에 넣었다.
        // 이 유닛은 transaction-type="RESOURCE_LOCAL" 이라 JTA가 아니라 em.getTransaction()으로 트랜잭션을 연다.
        //
        // createEntityManager()가 비어 있는 영속성 컨텍스트를 하나 만든다.
        // 이 시점에는 SELECT도, 트랜잭션도 없다. JDBC 커넥션도 아직 빌리지 않는 구현이 많다.
        // 닫는 순서는 선언의 역순이다. 먼저 em.close()로 컨텍스트를 없애고, 그다음 emf.close()로 풀을 반환한다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt");
             EntityManager em = emf.createEntityManager();) {
            // DB 트랜잭션 시작. 커넥션을 얻어 auto-commit을 끄고 BEGIN 한다.
            // 이 트랜잭션이 끝나기 전의 SELECT/UPDATE는 모두 같은 커넥션, 같은 트랜잭션에 속한다.
            // 영속성 컨텍스트 자체는 이미 EM 생성 때 만들어져 있고, begin은 그 컨텍스트를 이 트랜잭션에 묶는 단계다.
            em.getTransaction().begin();

            try {
                // 1) 영속성 컨텍스트(1차 캐시)에서 JpaPost + id=1을 찾는다. 방금 만든 컨텍스트라 비어 있다.
                // 2) 없으면 DB로 SELECT ... FROM jpa_post WHERE id = 1 을 보낸다.
                // 3) 행이 있으면 Hibernate가 protected 기본 생성자로 객체를 만들고 컬럼 값을 채운 뒤 영속 상태로 등록한다.
                //    이때 로드된 값의 스냅샷을 컨텍스트가 따로 기억해 둔다. 나중에 이 스냅샷과 현재 필드를 비교한다.
                //    member는 FetchType.LAZY 라서 회원 테이블을 지금 조회하지 않고, 프록시만 넣어 둔다.
                //    getMember()를 실제로 호출하기 전에는 jpa_member SELECT가 나가지 않는다.
                // 4) 행이 없으면 null. 컨텍스트에는 아무것도 들어가지 않는다.
                JpaPost post = em.find(JpaPost.class, postId);
                if (post == null) {
                    System.out.println("not found");
                    // 변경은 없지만 begin으로 연 트랜잭션은 반드시 끝내야 한다.
                    // 커밋할 내용이 없으므로 롤백으로 트랜잭션만 닫는다. DB에 쓸 SQL은 없다.
                    em.getTransaction().rollback();
                    // return이어도 try-with-resources가 em, emf를 닫는다.
                    // 이 분기는 예외가 아니므로 아래 catch로는 들어가지 않는다.
                    return;
                }

                // post는 지금 영속 상태다. edit는 그 객체의 title, body 필드만 바꾼다.
                // SQL은 아직 없다. persist, merge, update를 호출하지도 않는다.
                // 컨텍스트 안에 있는 인스턴스의 필드를 고쳤기 때문에, flush 때 스냅샷과 달라진 것이 감지된다.
                // 만약 post가 준영속이었다면 이 대입만으로는 DB가 바뀌지 않는다. 그때는 merge가 필요하다.
                // title/body가 비었거나 길이 제한을 넘으면 edit 안의 validate가 RuntimeException을 던지고, 아래 catch로 간다.
                post.edit("JPA edited", "dirty checking");
                // commit이 DB COMMIT을 바로 치지 않는다. 그 전에 flush가 자동으로 실행된다(기본 FlushMode.AUTO).
                // flush는 컨텍스트의 영속 엔티티를 돌며 스냅샷과 현재 값을 비교한다.
                // title, body가 달라졌으므로 UPDATE jpa_post SET ... WHERE id = 1 이 나간다.
                // "변경이 있다"를 판단하는 것이 dirty checking이고, 그 결과로 UPDATE가 만들어진다.
                // 기본 Hibernate는 동적 UPDATE가 꺼져 있어서, 바뀐 컬럼만 골라 SET에 넣기보다
                // 변경이 감지되면 매핑된 수정 가능 컬럼을 UPDATE에 포함하는 쪽이 일반적이다.
                // UPDATE가 성공한 뒤에야 JDBC commit이 되고, 그때 비로소 다른 커넥션에서도 새 값이 보인다.
                //
                // 이 EM은 확장 영속성 컨텍스트라서 commit 이후에도 post는 영속이다.
                // 스냅샷은 방금 커밋한 값으로 맞춰지고, EM을 닫거나 clear 하기 전에는 준영속이 아니다.
                em.getTransaction().commit();
                // getTitle()은 메모리에 있는 필드를 읽는다. commit 이후에 SELECT가 다시 나가지 않는다.
                System.out.println(post.getTitle());

            } catch (RuntimeException e) {
                // commit 전에 예외가 나면(검증 실패, flush 중 DB 오류 등) 트랜잭션이 아직 살아 있을 수 있다.
                // 살아 있으면 롤백해서, 이 트랜잭션에서 나간 SQL을 DB가 취소하게 한다.
                // 롤백되면 명세상 영속 엔티티는 준영속이 된다.
                // 메모리의 post.title은 이미 "JPA edited"인데 DB는 예전 값이라, 둘을 그대로 두면 다음 flush가 위험하다.
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                // clear는 1차 캐시를 비우고 관리 중이던 엔티티를 모두 준영속으로 만든다.
                // flush되지 않은 변경은 DB에 쓰지 않고 버린다.
                // 롤백만으로도 구현체가 컨텍스트를 정리하는 경우가 있지만, 여기서는 남은 변경을 명시적으로 떨어뜨린다.
                em.clear();
                throw e;
            }
            // 정상 경로로 이 블록을 빠져나가도, 바깥 try-with-resources가 em.close()를 호출한다.
            // close가 영속성 컨텍스트를 끝내므로, 그때까지 영속이던 post는 준영속이 된다.
            // 준영속 객체의 필드는 메모리에 남아 있지만, 이후 필드를 고쳐도 이 EM은 더 이상 UPDATE를 만들지 않는다.
        }
    }
}
