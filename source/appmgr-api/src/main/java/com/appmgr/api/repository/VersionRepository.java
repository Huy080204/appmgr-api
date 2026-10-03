package com.appmgr.api.repository;

import com.appmgr.api.model.Version;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Optional;

public interface VersionRepository extends JpaRepository<Version, Long>, JpaSpecificationExecutor<Version> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Version v WHERE v.application.id = :applicationId")
    void deleteAllByApplicationId(@Param("applicationId") Long applicationId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Version v WHERE v.application.id IN (SELECT a.id FROM Application a WHERE a.project.id = :projectId)")
    void deleteAllByApplicationProjectId(@Param("projectId") Long projectId);

    Optional<Version> findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(Long id, Long applicationId, Long channelId, Integer type, Integer status);

    Optional<Version> findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(Long applicationId, Long categoryId, Long channelId, Collection<Integer> types, Integer status);
}
