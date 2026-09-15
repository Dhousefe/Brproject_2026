#!/usr/bin/env python3
"""Convert the MariaDB HeidiSQL dump at data/Backup.sql into SQLite.

Designed for BrProject:
- creates SQLite schema from db/migrations/sqlite/000_schema.sql;
- applies mandatory high-performance PRAGMAs;
- loads HeidiSQL REPLACE INTO batches as INSERT OR REPLACE;
- handles semicolons and commas inside quoted strings.
"""
from __future__ import annotations
import argparse, sqlite3
from pathlib import Path


def split_sql_statements(sql: str):
    out, cur = [], []
    quote = None
    escape = False
    for ch in sql:
        cur.append(ch)
        if escape:
            escape = False
            continue
        if ch == "\\":
            escape = True
            continue
        if quote:
            if ch == quote:
                quote = None
            continue
        if ch in ("'", '"'):
            quote = ch
            continue
        if ch == ';':
            stmt = ''.join(cur).strip()
            if stmt:
                out.append(stmt)
            cur = []
    tail = ''.join(cur).strip()
    if tail:
        out.append(tail)
    return out


def normalize_insert(stmt: str) -> str | None:
    idx = stmt.upper().find('REPLACE INTO `')
    if idx < 0:
        return None
    stripped = stmt[idx:].strip()
    return stripped.replace('REPLACE INTO `', 'INSERT OR REPLACE INTO "', 1).replace('` (', '" (', 1).replace('`', '"')


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', default='data/Backup.sql')
    ap.add_argument('--schema', default='db/migrations/sqlite/000_schema.sql')
    ap.add_argument('--indexes', default='db/migrations/sqlite/001_indexes.sql')
    ap.add_argument('--output', default='data/db/brproject.sqlite')
    args = ap.parse_args()

    root = Path.cwd()
    out = root / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()

    conn = sqlite3.connect(out)
    schema_sql = (root / args.schema).read_text(encoding='utf-8').replace('DEFAULT current_timestamp()', 'DEFAULT CURRENT_TIMESTAMP')
    conn.executescript(schema_sql)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA synchronous=NORMAL')
    conn.execute('PRAGMA cache_size=-64000')
    conn.execute('PRAGMA temp_store=MEMORY')
    conn.execute('PRAGMA mmap_size=268435456')
    conn.execute('PRAGMA busy_timeout=10000')

    total = 0
    warnings = 0
    sql = (root / args.input).read_text(encoding='utf-8', errors='replace')
    for raw in split_sql_statements(sql):
        normalized = normalize_insert(raw)
        if not normalized:
            continue
        try:
            conn.executescript(normalized)
            total += normalized.count('),') + 1
        except sqlite3.Error as exc:
            warnings += 1
            table = raw.split('`', 2)[1] if '`' in raw else '<unknown>'
            print(f'[WARN] skipped data for {table}: {exc}')

    idx = root / args.indexes
    if idx.exists():
        conn.executescript(idx.read_text(encoding='utf-8'))
    conn.commit()
    tables = conn.execute("SELECT count(*) FROM sqlite_master WHERE type='table'").fetchone()[0]
    rows = sum(row[0] for row in conn.execute("SELECT count(*) FROM sqlite_master WHERE type='table'"))
    conn.close()
    print(f'created {out} tables={tables} rows~={total} warnings={warnings}')
    return 0 if warnings == 0 else 2


if __name__ == '__main__':
    raise SystemExit(main())
