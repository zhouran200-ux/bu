# Database migration test coverage

`AppDatabaseMigrationTest` retains two explicit kinds of coverage:

- **Legacy SQLite behavior (versions below 26):** the repository and its available, non-shallow Git history contain no exported Room schemas for versions 2–25. The earliest snapshot is `eb137d4`. These tests therefore do not claim historical Room schema validation. They create a minimal version-2 SQLite baseline containing the pre-migration columns required by the retained production SQL: `notes` (`id`, `title`, `body`, `updatedAt`), `tasks` (`id`, `title`, `isDone`, `createdAt`), and `chat_messages` with its original nullable attachment fields. The actual preceding production migrations build each source-version fixture; test data is inserted before the migration under test. `SupportSQLiteOpenHelper` runs the supplied contiguous upgrade path, and the existing data/default/index/table assertions remain active. The seeded 2→18 chain also remains active. No missing-schema case is skipped.
- **Exported Room schema validation (26→27→28→29→30):** these tests continue using `MigrationTestHelper.runMigrationsAndValidate` with dropped-table validation enabled against the checked-in schema exports. Inserts explicitly supply required columns: Kotlin entity defaults are not SQL defaults in these exports.

The legacy baseline is a migration-contract fixture, not a recovered original Room schema. It cannot establish that an unarchived historical entity definition exactly matched each intermediate database. Recovering authentic old schema exports would permit additional independent schema validation; fabricating Room identity hashes or relabeling current schemas as historical ones would not.

## Known historical FTS risk

Static inspection of production `MIGRATION_17_18` shows that its FTS5 external-content tables declare a `content` column, while the corresponding base tables use `notes.body` and `tasks.notes`. On a SQLite build with FTS5, rebuilding or reading those indexes can fail because external-content column names must match. The migration catches the rebuild exception without removing partially created tables and triggers. The existing FTS assertions remain unchanged, so the cloud emulator may expose this historical production defect. This fixture repair does not change production migrations or claim the FTS path passes.

Android compilation and execution belong in GitHub Actions, not local Java/Gradle runs. The instrumentation suite must expose actual SQLite/FTS compatibility failures instead of suppressing them merely to make CI pass.
