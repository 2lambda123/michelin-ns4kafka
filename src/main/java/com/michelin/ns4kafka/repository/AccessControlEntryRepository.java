package com.michelin.ns4kafka.repository;

import com.michelin.ns4kafka.model.AccessControlEntry;
import java.util.Collection;
import java.util.Optional;

/**
 * Access control entry repository.
 */
public interface AccessControlEntryRepository {
    Collection<AccessControlEntry> findAll();

    Optional<AccessControlEntry> findByName(String namespace, String name);

    AccessControlEntry create(AccessControlEntry accessControlEntry);

    void delete(AccessControlEntry accessControlEntry);
}
