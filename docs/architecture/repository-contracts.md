# Repository contracts

This document records the persistence contracts introduced during Phase 4. Game
logic must depend on these interfaces and services rather than constructing SQL
or JDBC statements directly.

## `CharacterStore`

`ext.mods.gameserver.data.repository.CharacterStore` owns the persistence
operations required by the character lifecycle:

- `findClanId(objectId)` reads the clan relationship needed before deletion;
- `updateDeleteTime(objectId, deleteTime)` schedules or restores deletion;
- `deleteCharacter(objectId)` removes the character and all owned records.

The contract deliberately does not expose `Connection`, `PreparedStatement`,
SQL strings, or a database-specific type. `JdbcCharacterStore` is the current
adapter and is responsible for SQL dialect details and connection handling.

### Transaction boundary

`deleteCharacter` is atomic from the caller's perspective: all related rows are
deleted in one database transaction, with rollback on failure. Cache and event
notifications happen only after the database operation succeeds, in the
application service `CharacterLifecycleService`.

### Migration rule

New game code must call the repository/service contract. A database-specific
query found outside an adapter is a Phase 4 cleanup candidate and should be
registered before being migrated. The adapter remains replaceable by another
implementation (for example, a future MariaDB or PostgreSQL-specific adapter)
without changing the game flow.

## `PremiumStore`

`ext.mods.gameserver.data.repository.PremiumStore` centralizes the
`account_premium` table used by player login, the donation item flow and the
administrator premium commands. Its operations preserve the existing behavior:

- `upsert` creates or updates the service and expiration timestamp;
- `find` reads the account state without creating it;
- `expire` clears an existing row;
- `delete` removes the row completely.

The JDBC implementation is `JdbcPremiumStore`, exposed to game code through
`PremiumPersistenceService`. This prevents the same table and dialect-specific
upsert logic from being repeated across unrelated handlers.

## `RecommendationStore`

`ext.mods.gameserver.data.repository.RecommendationStore` owns the
`character_recommends` row and the two counter updates that must accompany it.
`addRecommendation` commits all three writes atomically, while
`loadGivenRecommendations` restores the giver's target list. The in-memory
rollback behavior remains in `PlayerRecom`; database transaction handling is
implemented by `JdbcRecommendationStore`.

## `SubclassStore`

`ext.mods.gameserver.data.repository.SubclassStore` owns subclass slots and
the class-indexed data removed during a subclass replacement. `wipe` deletes
the subclass, hennas, shortcuts, saved effects and skills in one transaction;
the subsequent creation of the replacement slot remains orchestrated by
`PlayerSubClass`, preserving the existing game flow.

## `SkillStore`

`ext.mods.gameserver.data.repository.SkillStore` owns character skill rows
and saved effect/cooldown state. `JdbcSkillStore.replaceSkillSaves` replaces
the saved state in one transaction, while `PlayerSkillsDb` continues to own
skill lookup, effect construction and runtime cooldown decisions.
