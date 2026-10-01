package com.appmgr.api.repository;

import com.appmgr.api.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {
    boolean existsByNameAndProjectId(String name, Long projectId);

    boolean existsByNameAndProjectIdAndIdNot(String name, Long projectId, Long id);

    void deleteAllByProjectId(Long projectId);
}
