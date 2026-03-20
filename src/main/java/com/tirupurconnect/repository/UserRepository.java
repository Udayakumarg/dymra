package com.tirupurconnect.repository;
import com.tirupurconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
    @Query("SELECT u FROM User u JOIN u.tenant t WHERE t.slug = :slug AND u.phone = :phone")
    Optional<User> findByTenantSlugAndPhone(@Param("slug") String slug, @Param("phone") String phone);
}
