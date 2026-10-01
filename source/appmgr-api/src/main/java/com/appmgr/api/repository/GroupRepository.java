package com.appmgr.api.repository;

import com.appmgr.api.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GroupRepository extends JpaRepository<Group, Long>, JpaSpecificationExecutor<Group> {
    Group findFirstByName(String name);

    Group findFirstByNameAndIdNot(String name, Long id);

    Group findFirstByKind(int kind);
}
