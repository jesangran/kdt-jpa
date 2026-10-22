package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * 시드(seed): 연습용 회원·글을 DB에 한 번 넣어 두는 프로그램.
 * 이 파일에서 처음으로 persist를 사용한다.
 *
 * 엔티티 상태
 * 1) 비영속(new/transient): new로만 만든 객체. JPA가 아직 모름. DB와 무관.
 * 2) 영속(managed): persist 후 EntityManager(영속성 컨텍스트)가 관리하는 객체.
 * 3) 준영속(detached): EM이 닫히거나 clear된 뒤. 객체는 남아 있지만 JPA가 더 이상 추적 안 함.
 * 4) 삭제(removed): remove 호출. 커밋 때 DELETE SQL.
 *
 * 영속성 컨텍스트는 1차 캐시다.
 * 같은 EM에서 같은 클래스·같은 PK로 find하면 SELECT를 한 번만 보내고,
 * 두 번째부터는 캐시의 그 인스턴스를 그대로 돌려준다. first == second 가 true인 이유다.
 */
public class JpaSeed {
    public static void main(String[] args) {
        // EntityManagerFactory: 앱에서 보통 1개. 설정·엔티티 메타데이터를 들고 있는 공장.
        // EntityManager: 실제 작업 단위. 영속성 컨텍스트(1차 캐시)를 가진다. 요청/작업마다 만들고 닫는다.
        // try-with-resources: 블록 끝에서 em.close(), emf.close() 자동 호출. 닫는 순서는 선언의 역순.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt");
             EntityManager em = emf.createEntityManager()) {

            // 트랜잭션: DB 변경(INSERT/UPDATE/DELETE)은 트랜잭션 안에서만 확정된다.
            // begin ~ commit 사이 persist한 내용이 커밋(또는 flush) 때 SQL로 나간다.
            em.getTransaction().begin();

            try {
                // 비영속: 자바 힙에만 있는 객체. 아직 INSERT 안 됨. id는 보통 null.
                JpaMember member = new JpaMember("jpa01", "kim");
                // persist: 영속성 컨텍스트에 등록 → 영속 상태.
                // IDENTITY 전략에서는 persist 시점에 INSERT가 나가 id가 채워지는 구현이 많다.
                // (다른 전략은 커밋/flush 때까지 id가 null일 수 있다.)
                em.persist(member);

                // member는 이미 영속. post가 그 member를 참조하므로 FK(member_id)를 넣을 수 있다.
                // 비영속 member를 그대로 넣고 persist(post)만 하면 연관관계/FK 오류가 나기 쉽다.
                JpaPost post = new JpaPost(member, "JPA Start", "pure JAVA");
                // post도 영속으로 등록. 커밋 때 INSERT 대상이 된다.
                em.persist(post);

                // 1차 캐시 확인용. 방금 persist한 글의 id는 IDENTITY라 이미 채워져 있을 수 있다.
                // 여기서는 id=1을 가정하고 두 번 find한다.
                int postId = 1;
                // 첫 find: 캐시에 없으면 SELECT 후 영속으로 등록.
                JpaPost first = em.find(JpaPost.class, postId);
                // 둘째 find: 같은 EM·같은 PK라 DB를 다시 치지 않고 first와 같은 인스턴스를 반환.
                JpaPost second = em.find(JpaPost.class, postId);
                // true면 1차 캐시가 동작한 것이다. (주소 비교)
                System.out.println(first == second);

                // commit: 영속성 컨텍스트의 변경을 DB에 반영(flush)한 뒤 트랜잭션을 확정한다.
                // 성공하면 표에 행이 생긴다. 이후 member/post의 id를 출력해 확인한다.
                em.getTransaction().commit();

                System.out.println("memberId= " + member.getId());
                System.out.println("postId= " + post.getId());

            } catch (RuntimeException e) {
                // 중간에 실패하면 커밋하지 않고 롤백 → 이번 트랜잭션의 INSERT는 전부 취소.
                // isActive: 이미 커밋/롤백된 트랜잭션에 또 rollback하지 않도록 확인.
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw e;
            }
            // 여기서 em/emf가 닫히면, 위에서 만든 객체는 DB 행과 별개로 "준영속"이 된다.
            // 객체는 살아 있어도, 더 이상 필드 변경이 자동 UPDATE로 이어지지 않는다.
        }
    }
}
