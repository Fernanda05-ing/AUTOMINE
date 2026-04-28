package com.automine.platform.repository;

import com.automine.platform.entity.SgsstIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SgsstIncidentRepository extends JpaRepository<SgsstIncident, Integer> {
}
