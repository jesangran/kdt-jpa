package chapter03.lesson7;

public class JpaStateCheck {
    public static void main(String[] args) {
        // 1. DB에 저장하지 않고 회원·초안 게시글을 만들어 Java 상태 규칙만 확인합니다.
        JpaMember member = new JpaMember("state-check", "tester");
        JpaPost post = new JpaPost(member, "state check", "body");
        System.out.println("initial=" + post.getStatus());

        // 2. 초안에는 댓글을 작성할 수 없으므로 공개 전에 실패하는지 확인합니다.
        try {
            new JpaComment(post, member, "first comment");
        } catch (IllegalStateException e) {
            System.out.println("draftCommentError=" + e.getMessage());
        }

        // 3. 게시글을 먼저 공개해야 댓글 생성자의 게시글 상태 검사를 통과합니다.
        post.publish();
        JpaComment comment = new JpaComment(post, member, "first comment");
        System.out.println("published=" + post.getStatus());
        System.out.println("comment=" + comment.getContent());

        // 4. 생성한 댓글을 공백으로 수정하면 내용 검증이 실패하는지 확인합니다.
        try {
            comment.edit("   ");
        } catch (IllegalArgumentException e) {
            System.out.println("blankError=" + e.getMessage());
        }

        // 5. 게시글을 보관한 뒤 게시글 상태와 댓글 작성 가능 여부를 함께 확인합니다.
        post.archive();
        System.out.println("archived=" + post.getStatus());
        System.out.println("canReceiveComment=" + post.canReceiveComments());
    }
}
