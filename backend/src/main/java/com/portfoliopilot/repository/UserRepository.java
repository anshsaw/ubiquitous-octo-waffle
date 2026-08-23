package com.portfoliopilot.repository;

import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.Role;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code users}.
 *
 * <p>Every finder filters {@code deleted = false}. Soft-deleted accounts must be
 * invisible to authentication and to normal listings; only the admin purge path
 * looks at them, and it uses {@code findById} directly.
 *
 * <p>Email is stored lowercase by {@code AuthService}, so a plain equality match
 * hits the {@code uniq_email} index. A case-insensitive derived query would
 * force a regex and lose the index.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /** Login path. Uses index {@code uniq_email}. */
    Optional<User> findByEmailAndDeletedFalse(String email);

    /** Public portfolio owner lookup + username availability. Uses {@code uniq_username}. */
    Optional<User> findByUsernameAndDeletedFalse(String username);

    Optional<User> findByIdAndDeletedFalse(String id);

    /**
     * Registration pre-check. The unique index remains the real guarantee - this
     * only produces a friendly error before hitting it.
     */
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** Admin stat card. Excludes staff accounts. */
    long countByRoleAndDeletedFalse(Role role);

    /** Signup-trend fallback and "new users this period" reporting. */
    long countByCreatedAtGreaterThanEqualAndDeletedFalse(Instant from);
}
