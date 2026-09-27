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
