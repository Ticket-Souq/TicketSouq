package org.ticketsouq.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ticketsouq.userservice.dto.MemberSummaryResponse;
import org.ticketsouq.userservice.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("SELECT new org.ticketsouq.userservice.dto.MemberSummaryResponse(u.id, u.email) " +
           "FROM User u " +
           "WHERE u.id IN :ids")
    List<MemberSummaryResponse> findMemberSummariesByIds(@Param("ids") List<UUID> ids);
}
