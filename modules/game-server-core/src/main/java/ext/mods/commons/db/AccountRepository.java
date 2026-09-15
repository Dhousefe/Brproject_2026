package ext.mods.commons.db;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Example Phase 5 repository for {@code accounts} table.
 * New code paths should prefer this style over raw SQL in call sites.
 * <p>
 * att-ver-3.0: never inserts empty password hashes — use {@link #saveWithPasswordHash}.
 */
public final class AccountRepository implements Repository<AccountRepository.AccountRow, String>
{
	public record AccountRow(String login, int accessLevel, long lastActive, int lastServer)
	{
	}
	
	private static final AccountRepository INSTANCE = new AccountRepository();
	
	public static AccountRepository getInstance()
	{
		return INSTANCE;
	}
	
	private AccountRepository()
	{
	}
	
	@Override
	public Optional<AccountRow> findById(String login)
	{
		return JdbcSupport.queryOne(
			"SELECT login, access_level, last_active, last_server FROM accounts WHERE login=?",
			ps -> ps.setString(1, login),
			rs -> new AccountRow(
				rs.getString("login"),
				rs.getInt("access_level"),
				rs.getLong("last_active"),
				rs.getInt("last_server")));
	}
	
	@Override
	public List<AccountRow> findAll()
	{
		return JdbcSupport.queryList(
			"SELECT login, access_level, last_active, last_server FROM accounts ORDER BY login",
			null,
			rs -> new AccountRow(
				rs.getString("login"),
				rs.getInt("access_level"),
				rs.getLong("last_active"),
				rs.getInt("last_server")));
	}
	
	/**
	 * Updates access/last_active/last_server only — does <b>not</b> create accounts
	 * and never writes an empty password.
	 */
	@Override
	public AccountRow save(AccountRow entity)
	{
		Objects.requireNonNull(entity, "entity");
		final int updated = JdbcSupport.update(
			"""
			UPDATE accounts SET access_level=?, last_active=?, last_server=? WHERE login=?
			""",
			ps ->
			{
				ps.setInt(1, entity.accessLevel());
				ps.setLong(2, entity.lastActive());
				ps.setInt(3, entity.lastServer());
				ps.setString(4, entity.login());
			});
		if (updated == 0)
			throw new IllegalStateException("Account not found for update: " + entity.login() + " (use saveWithPasswordHash to create)");
		return entity;
	}
	
	/**
	 * Create or update account with a non-empty password hash (BCrypt).
	 */
	public AccountRow saveWithPasswordHash(AccountRow entity, String passwordHash)
	{
		Objects.requireNonNull(entity, "entity");
		if (passwordHash == null || passwordHash.isBlank())
			throw new IllegalArgumentException("passwordHash must be a non-empty hash (never plain empty)");
		JdbcSupport.update(
			"""
			INSERT INTO accounts (login, password, last_active, access_level, last_server)
			VALUES (?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE password=VALUES(password), access_level=VALUES(access_level),
				last_active=VALUES(last_active), last_server=VALUES(last_server)
			""",
			ps ->
			{
				ps.setString(1, entity.login());
				ps.setString(2, passwordHash);
				ps.setLong(3, entity.lastActive());
				ps.setInt(4, entity.accessLevel());
				ps.setInt(5, entity.lastServer());
			});
		return entity;
	}
	
	@Override
	public boolean deleteById(String login)
	{
		return JdbcSupport.update("DELETE FROM accounts WHERE login=?", ps -> ps.setString(1, login)) > 0;
	}
}
