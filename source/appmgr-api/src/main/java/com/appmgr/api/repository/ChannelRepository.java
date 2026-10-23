package com.appmgr.api.repository;

import com.appmgr.api.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long>, JpaSpecificationExecutor<Channel> {
    Optional<Channel> findByName(String name);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
