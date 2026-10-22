package chapter03.lesson7;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * DB에 붙을 수 있는지, persistence.xml의 "kdt" 설정을 읽을 수 있는지만 확인하는 시작점.
 * 여기서는 저장/조회를 하지 않는다. EntityManager도 만들지 않는다.
 *
 * EntityManagerFactory가 만들어지면 보통
 * - persistence-unit 이름을 찾았고
 * - 엔티티 매핑을 읽었고
 * - JDBC 드라이버·URL로 연결을 준비할 수 있다는 뜻이다.
 * (실제 SELECT 1 같은 ping SQL을 반드시 보내는 구현만 있는 것은 아니다.)
 */
public class JpaPing {
    public static void main(String[] args) {
        // EntityManagerFactory: 엔티티 정보, DB 접속 정보, 커넥션 풀을 들고 있는 공장.
        // 만들 때 비용이 크므로 애플리케이션당 하나만 만들고, 끝날 때 close한다. 스레드에 안전하다.
        // "kdt"는 META-INF/persistence.xml의 persistence-unit name과 같아야 한다.
        //
        // 실제 SQL은 이 공장이 아니라, 공장에서 꺼낸 EntityManager가 실행한다.
        // EntityManager는 한 작업(트랜잭션) 단위이고, 스레드끼리 공유하면 안 된다.
        //
        // try-with-resources: 블록을 빠져나갈 때 emf.close()가 자동으로 호출된다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            System.out.println("emf ok");
        }

        //페이지 기법 : 전체를 가지고 오는게 아닌 부분적으로 가지고 오는 것 offset size

    }
}
