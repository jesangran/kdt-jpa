package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;

import java.util.List;

/**
 * JPQL로 글을 조회하는 예제.
 *
 * [바인딩: JDBC vs JPQL]
 * 둘 다 값을 쿼리 문자열에 직접 붙이지 않고 "바인딩"한다.
 * ("..." + value 로 이어 붙이면 SQL 인젝션 위험이 있다.)
 *
 * JDBC (PreparedStatement)
 * - 자리 표시는 보통 ?
 * - setString(1, loginId) 처럼 순서(1부터)로 넣는다.
 *
 *   String sql = "SELECT * FROM member WHERE login_id = ?";
 *   ps.setString(1, loginId);
 *
 * JPQL
 * - 이름 파라미터가 흔하다: :loginId → setParameter("loginId", loginId)
 * - 위치 파라미터도 가능하다: ?1, ?2 → setParameter(1, loginId)
 * - JDBC처럼 그냥 ? 만 쓰는 방식은 JPQL 표준이 아니다.
 *
 *   "select m from JpaMember m where m.loginId = :loginId"
 *   .setParameter("loginId", loginId)
 *
 * find(PK)는 기본키로 한 건. 아래처럼 조건·목록은 createQuery(JPQL)을 쓴다.
 * JPQL은 테이블/컬럼 이름이 아니라 엔티티·필드 이름을 쓴다. (JpaPost, p.title)
 *
 * [join vs join fetch / N+1]
 * - 일반 join: 연관(member)을 조건·연결용으로만 묶는다. LAZY member를 채워 준다는 보장 없음.
 * - join fetch: 연관까지 한 번의 SELECT(JOIN)로 같이 로딩한다. getMember() 때 추가 SELECT가 거의 없다.
 * - N+1: 글 N건 조회(1) 후, 각 글마다 getMember()로 회원 SELECT(+N) → 쿼리 1+N번.
 *   그래서 목록에서 작성자를 같이 쓸 때 join fetch로 N+1을 줄인다.
 *   일반 join만으로는 N+1이 안 없어질 수 있다.
 */
public class JpaPostQuery {
    public static void main(String[] args) {

        String keyword = "JPA";
        String exactTitle = "JPA edited";
        String wantedLoginId = "jpa01";

        // "kdt" 유닛으로 팩토리·EM을 연다. 조회만 해도 RESOURCE_LOCAL이면
        // 구현에 따라 트랜잭션을 열지 않고 getResultList만 호출하는 경우가 많다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt");
             EntityManager em = emf.createEntityManager();) {

            // --- 전체 목록: 파라미터 없음 ---
            System.out.println("all");
            // select p from JpaPost p → 엔티티 JpaPost를 별칭 p로 조회. order by p.id
            TypedQuery<JpaPost> allQuery = em.createQuery("select p from JpaPost p order by p.id", JpaPost.class);

            // getResultList: 결과가 0건 이상이면 List로 받는다. 한 건만이면 getSingleResult도 있다.
            List<JpaPost> allPost = allQuery.getResultList();
            System.out.println("allCount= " + allPost.size()); //allCount= 1

            for (JpaPost post : allPost) {
                System.out.println(post.getId() + "|" + post.getTitle()); //1|JPA Start
            }

            // 1차 캐시를 비운다. 아래 조회가 캐시 객체를 재사용하지 않고 DB/쿼리 쪽으로 가도록 할 때 쓴다.
            em.clear();

            // --- 제목 부분 일치: 이름 파라미터 :keyword ---
            System.out.println("normal");
            // like :keyword → JDBC의 ? 자리에 해당. setParameter로 값을 넣는다.
            // "%" + keyword + "%" 는 like 와일드카드를 자바에서 붙인 것이다. JPQL 문자열 안에 값을 넣지 않는다.
            List<JpaPost> normalPosts =
                    em.createQuery("select p from JpaPost p where p.title like :keyword order by p.id", JpaPost.class)
                            .setParameter("keyword", "%" + keyword + "%").getResultList();

            System.out.println("normalCount= " + normalPosts.size()); //normalCount= 1
            // printPosts가 getMember()를 호출한다 → LAZY라 글마다(또는 회원마다) 회원 SELECT가 나갈 수 있다.
            // 글 목록 1번 + 회원 N번 = N+1. 아래 로그가 그 예시다.
            printPosts(normalPosts);
            /*
               select
                jm1_0.id,
                jm1_0.login_id,
                jm1_0.nickname
               from jpa_member jm1_0
               where jm1_0.id=?
            * */

            em.clear();
            // [일반 join]
            // - 목적: 연관(member)을 조건·연결용으로 묶는다. (작성자까지 미리 채운다는 보장 없음)
            // - getMember()를 호출하면 여전히 회원 SELECT가 따로 나갈 수 있다 → N+1 가능
            // - SQL 로그에 JOIN이 안 보일 수 있다:
            //   1) select 가 p 만 있고
            //   2) where 에서도 m(회원 필드)을 쓰지 않으며 (제목 like 만 사용)
            //   3) member_id 는 이미 jpa_post 에 있고 optional=false 라서
            //   → Hibernate가 "불필요한 JOIN"으로 보고 SQL에서 빼 버린다.
            //   JOIN이 보이게: where m.loginId = :wantedLoginId 또는 아래 join fetch
            System.out.println("join");

            List<JpaPost> joinPosts = em.createQuery("select p from JpaPost p join p.member m where p.title like :keyword order by p.id", JpaPost.class)
                    .setParameter("keyword", "%" + keyword + "%").getResultList();

            System.out.println("joincount= "+joinPosts.size());

            // [join fetch]
            // - 목적: 연관(member)까지 한 번의 SELECT(JOIN)로 같이 로딩한다.
            // - printPosts에서 getMember()를 호출해도 추가 회원 SELECT가 거의 없다 → N+1 완화
            // - 일반 join과 차이: join은 "연결", join fetch는 "연결 + 로딩"
            em.clear();
            System.out.println("fetch");

            List<JpaPost> fetchPosts = em.createQuery("select p from JpaPost p join fetch p.member m where p.title like :keyword order by p.id", JpaPost.class)
                    .setParameter("keyword", "%" + keyword + "%").getResultList();

            System.out.println("fetchJoin count= "+fetchPosts.size());

            //제목일치검색 제목 전체 비교
            //여기서는 id와 title만 출력해서 getMember()를 호출하지 않는다. 회원 지연 로딩 SQL은 없다.
            em.clear();
            System.out.println("exact");

            List<JpaPost> exactPosts = em.createQuery("select p from JpaPost p where p.title = :title order by p.id",JpaPost.class)
                    .setParameter("title", "%" + keyword + "%").getResultList();

            System.out.println("exactCount= "+exactPosts.size());

            for (JpaPost post : exactPosts) {
                System.out.println(post.getId() + "|" + post.getTitle());
            }

            //회원일치검색
            em.clear();
            System.out.println("member");
            List<JpaMember> members = em.createQuery("select m from JpaMember m where m.loginId = :loginId",JpaMember.class)
                    .setParameter("loginId", wantedLoginId)
                    .getResultList();
            System.out.println("memberCount= "+members.size());

            if(members.isEmpty()) {
                // getSingleResult()는 0건이면 예외다. 목록으로 받아서 없음을 직접 구분한다.
                System.out.println("memeber not found");
            }else {
                //loginId는 unique이므로 최대 1건만 검색 결과로 나옴
                // members.get(0)도 영속 상태다. 이 메서드가 끝날 때 em.close()가 호출되면 준영속이 된다.
                JpaMember member = members.get(0);
                System.out.println(member.getLoginId());
            }

        }
    }

    private static void printPosts(List<JpaPost> posts) {
        // main의 EntityManager가 아직 열려 있으므로 LAZY getMember()가 가능하다.
        // 글이 여러 건이고 member가 아직 안 채워져 있으면, 여기서 회원 SELECT가 N번 나갈 수 있다(N+1).
        // fetch join으로 미리 채웠다면 이 루프에서는 추가 SELECT가 거의 없다.
        //이 메서드를 em.close() 다음에 부르면, 초기화되지 않은 프록시에서 예외
        for (JpaPost post : posts) {
            System.out.println(post.getId() + "|"
                    + post.getMember().getLoginId() + "|" + post.getTitle()); //1|jpa01|JPA Start
        }
    }

}
