package com.portfoliopilot.repository;

import com.portfoliopilot.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** {@code profiles}. One document per user, enforced by the {@code uniq_userId} unique index. */
@Repository
public interface ProfileRepository extends MongoRepository<Profile, String> {

    /** The hottest authenticated read in the application. */
    Optional<Profile> findByUserId(String userId);

    void deleteByUserId(String userId);

    boolean existsByUserId(String userId);
}
