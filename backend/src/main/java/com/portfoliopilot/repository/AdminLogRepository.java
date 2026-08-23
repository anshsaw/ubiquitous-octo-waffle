package com.portfoliopilot.repository;

import com.portfoliopilot.model.AdminLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** {@code adminLogs} - append-only. There is intentionally no update or delete method. */
@Repository
public interface AdminLogRepository extends MongoRepository<AdminLog, String> {

    Page<AdminLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** "What was done TO this user?" - shown on the admin user-detail page. */
    List<AdminLog> findTop20ByTargetUserIdOrderByCreatedAtDesc(String targetUserId);
}
