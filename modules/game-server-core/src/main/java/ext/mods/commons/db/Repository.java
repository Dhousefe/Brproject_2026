package ext.mods.commons.db;

import java.util.List;
import java.util.Optional;

/**
 * Phase 5 minimal JDBC repository contract for new data access.
 * Existing legacy tables may keep ad-hoc SQL; new flows should implement this.
 *
 * @param <T> entity type
 * @param <ID> primary key type
 */
public interface Repository<T, ID>
{
	Optional<T> findById(ID id);
	
	List<T> findAll();
	
	T save(T entity);
	
	boolean deleteById(ID id);
}
