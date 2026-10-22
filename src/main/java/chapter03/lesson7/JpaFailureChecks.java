package chapter03.lesson7;

import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;

/**
 * JPA/DB 실패 원인을 Throwable 체인에서 판별하는 유틸.
 * commit·flush 때 예외가 여러 겹으로 감싸질 수 있어, getCause()를 따라가며 확인한다.
 */
public class JpaFailureChecks {

    /*
     * 낙관적 락(@Version) 충돌인지 확인한다.
     * - OptimisticLockException: 저장 시 version 이 맞지 않을 때 JPA가 던짐.
     * - 겉예외가 PersistenceException 등으로 감싸져 있을 수 있어,
     *   error → getCause() → getCause() ... 를 순회하며 중간에 있으면 true.
     */
    public static boolean isOptimisticLock(Throwable error) {
        /* 커밋 예외가 원래 낙관적 락 예외를 감쌀 수 있어 원인까지 확인합니다. */
        Throwable current = error;
        while (current != null) {
            if (current instanceof OptimisticLockException) return true;
            current = current.getCause();
        }
        return false;
    }

    /*
     * MySQL 중복 키(UNIQUE/PK 위반)인지 확인한다.
     * - errorCode 1062 = Duplicate entry (중복 키).
     * - FK 위반은 1452 등 다른 코드라서, 1062만 보면 "같은 loginId 두 번 INSERT" 같은
     *   중복과 FK 깨짐을 구분할 수 있다.
     * - SQLException도 감싸져 있을 수 있어 cause 체인을 따라간다.
     */
    public static boolean isDuplicateKey(Throwable error) {
        /* MySQL 1062만 중복 키로 판단합니다. FK 위반(1452)과 구분하기 위해서입니다. */
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
            current = current.getCause();
        }
        return false;
    }
}
