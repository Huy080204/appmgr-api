package com.appmgr.api.repository;

import com.appmgr.api.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
