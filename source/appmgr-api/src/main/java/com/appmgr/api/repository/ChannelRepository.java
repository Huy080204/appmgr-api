package com.appmgr.api.repository;

import com.appmgr.api.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChannelRepository extends JpaRepository<Channel, Long>, JpaSpecificationExecutor<Channel> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
