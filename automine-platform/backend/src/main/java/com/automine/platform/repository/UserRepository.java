package com.automine.platform.repository;

import com.automine.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByNombre(String nombre);
    Optional<User> findByEmail(String email);
    List<User> findAll();
}
