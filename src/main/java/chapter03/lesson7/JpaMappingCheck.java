package chapter03.lesson7;

/**
 * DB 저장 없이, JpaPost 생성자의 자바 검증(제목 길이)만 확인한다.
 * persist / find / JPQL을 쓰지 않으므로 DB 행 수는 변하지 않는다.
 *
 * 엔티티 생성자·validate는 DB 제약(NOT NULL, VARCHAR 길이)과 같은 규칙을
 * 자바에서도 먼저 검사한다. DB만 믿으면 잘못된 값이 영속 상태까지 들어가고,
 * flush 시점에야 예외가 난다.
 *
 * EntityManagerFactory도 열지 않는다. 순수 메모리 객체 검증이다.
 */
public class JpaMappingCheck {
    public static void main(String[] args) {
        // protected 기본 생성자는 JPA가 조회 결과를 채울 때 쓰는 입구다.
        // 앱 코드에서 new JpaMember() 는 컴파일/접근 오류다.
        // 값이 있는 생성자로 비영속 객체만 만든다. persist하지 않으므로 INSERT는 없다.
        JpaMember member = new JpaMember("mapping01", "kim");
        // "a"를 200번 이어 붙인 문자열. @Column(length = 200) 상한과 같다.
        String title200 = "a".repeat(200);

        // 200자 → validate 통과 → 비영속 JpaPost가 만들어진다.
        JpaPost valid = new JpaPost(member, title200, "body");
        System.out.println("title200.length =" + valid.getTitle().length());

        // 201자 → validate가 IllegalArgumentException을 던진다.
        // catch에서 메시지만 출력하고, 프로그램은 계속 끝난다.
        try {
            new JpaPost(member, "a".repeat(201), "body");
        } catch (IllegalArgumentException e) {
            // Exception으로 잡으면 이 프로그램의 다른 오류까지 여기서 삼킬 수 있다.
            // 검증 실패만 보려면 IllegalArgumentException이 맞다.
            System.out.println(e.getMessage());
        }
    }
}
