package com.appmgr.api.repository;

import com.appmgr.api.model.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Optional;

public interface VersionRepository extends JpaRepository<Version, Long>, JpaSpecificationExecutor<Version> {

    @Override
    @EntityGraph(attributePaths = {"application"})
    Optional<Version> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"application", "application.project", "channel"})
    Page<Version> findAll(Specification<Version> spec, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM Version v WHERE v.application.id = :applicationId")
    void deleteAllByApplicationId(@Param("applicationId") Long applicationId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Version v WHERE v.application.id IN (SELECT a.id FROM Application a WHERE a.project.id = :projectId)")
    void deleteAllByApplicationProjectId(@Param("projectId") Long projectId);

    boolean existsByChannelId(Long channelId);

    Optional<Version> findByIdAndApplicationTypeAndStatus(Long id, Integer type, Integer status);

    @EntityGraph(attributePaths = {"application"})
    Optional<Version> findFirstByApplicationIdAndChannelIdAndApplicationTypeInAndStatusOrderByTimestampDesc(Long applicationId, Long channelId, Collection<Integer> types, Integer status);

    Optional<Version> findFirstByApplicationIdAndChannelIdAndRuntimeVersionAndApplicationTypeAndStatusOrderByTimestampDesc(Long applicationId, Long channelId, String runtimeVersion, Integer type, Integer status);
}
