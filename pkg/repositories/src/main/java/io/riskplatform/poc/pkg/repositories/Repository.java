package io.riskplatform.poc.pkg.repositories;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD repository port.
 * Implementations live in the infrastructure layer.
 *
 * @param <T>  aggregate root type
 * @param <ID> identifier type
 */
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    void save(T entity);
    void delete(ID id);
}
