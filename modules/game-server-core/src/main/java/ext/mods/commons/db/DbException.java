package ext.mods.commons.db;

/**
 * Unchecked DB failure for repository layer (Phase 5).
 */
public class DbException extends RuntimeException
{
	public DbException(String message)
	{
		super(message);
	}
	
	public DbException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
