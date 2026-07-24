#!/usr/bin/env python3
"""Regenerate docs/database/ERD.html from a live PostgreSQL schema."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
ERD_PATH = ROOT / "docs" / "database" / "ERD.html"
SCHEMA_SCRIPT = re.compile(
    r'(<script id="schema-data" type="application/json">).*?(</script>)',
    re.DOTALL,
)
LEGACY_TIMETABLE_APPEND = re.compile(
    r"\n      schema\.flywayVersion = .*?"
    r"\n      schema\.foreignKeys\.push\(\.\.\.timetableForeignKeys\);\n",
    re.DOTALL,
)

DOMAINS = {
    "identity": {
        "label": "인증·사용자",
        "tables": {
            "users",
            "social_identities",
            "student_profiles",
            "privacy_consents",
            "login_otp_challenges",
        },
    },
    "catalog": {
        "label": "현재 강의 카탈로그",
        "tables": {
            "semesters",
            "courses",
            "sections",
            "sessions",
            "rooms",
            "data_imports",
        },
    },
    "timetable": {
        "label": "시간표·자동 편성",
        "tables": {
            "timetables",
            "timetable_courses",
            "timetable_course_meetings",
            "optimization_jobs",
            "optimization_job_excluded_days",
            "optimization_job_required_sections",
            "optimization_results",
            "optimization_result_course_slots",
        },
    },
    "academic-unit": {
        "label": "학과·전공 기준정보",
        "tables": {
            "academic_colleges",
            "academic_units",
            "academic_unit_aliases",
            "section_academic_units",
        },
    },
    "user-academic": {
        "label": "사용자 학사 데이터",
        "tables": {"course_reviews", "completed_courses"},
    },
    "history": {
        "label": "과거 학사 원장",
        "tables": {
            "historical_archive_manifests",
            "historical_term_datasets",
            "historical_course_offerings",
            "historical_curriculum_datasets",
            "historical_curriculum_departments",
            "historical_relation_datasets",
            "historical_course_relations",
        },
    },
    "curriculum": {
        "label": "교육과정 필수요건",
        "tables": {
            "requirement_datasets",
            "curriculum_program_requirements",
            "curriculum_program_aliases",
            "curriculum_required_courses",
            "graduation_requirement_rules",
        },
    },
    "graduation-credit": {
        "label": "졸업학점·교양",
        "tables": {
            "graduation_liberal_requirement_sets",
            "graduation_liberal_required_courses",
            "graduation_liberal_course_aliases",
            "graduation_liberal_course_terms",
            "graduation_liberal_area_requirements",
            "graduation_credit_profiles",
            "graduation_credit_profile_source_refs",
            "graduation_credit_profile_academic_unit_aliases",
            "graduation_credit_profile_warnings",
        },
    },
    "graduation-assessment": {
        "label": "졸업인증·과거요건",
        "tables": {
            "graduation_assessment_profiles",
            "graduation_assessment_source_refs",
            "graduation_assessment_categories",
            "graduation_assessment_credentials",
            "graduation_legacy_requirements",
            "graduation_legacy_source_refs",
            "graduation_legacy_cohorts",
        },
    },
    "operations": {
        "label": "적재 운영",
        "tables": {"reference_data_imports"},
    },
}

DELETE_ACTIONS = {
    "a": "NO ACTION",
    "r": "RESTRICT",
    "c": "CASCADE",
    "n": "SET NULL",
    "d": "SET DEFAULT",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--docker-container")
    parser.add_argument("--db-user", default="pl_timetable")
    parser.add_argument("--db-name", default="pl_timetable")
    parser.add_argument("--database-url")
    return parser.parse_args()


class Database:
    def __init__(self, args: argparse.Namespace) -> None:
        if args.docker_container:
            self.command = [
                "docker",
                "exec",
                args.docker_container,
                "psql",
                "-U",
                args.db_user,
                "-d",
                args.db_name,
            ]
        else:
            self.command = ["psql"]
            if args.database_url:
                self.command.extend(["--dbname", args.database_url])

    def query(self, sql: str) -> list[str]:
        result = subprocess.run(
            [*self.command, "-X", "-A", "-t", "-q", "-v", "ON_ERROR_STOP=1", "-c", sql],
            check=True,
            text=True,
            capture_output=True,
        )
        return [line for line in result.stdout.splitlines() if line]

    def scalar(self, sql: str) -> str:
        rows = self.query(sql)
        if len(rows) != 1:
            raise RuntimeError(f"expected one row, received {len(rows)}")
        return rows[0]

    def json_rows(self, sql: str) -> list[dict]:
        return [json.loads(row) for row in self.query(sql)]


def domain_for(table_name: str) -> tuple[str, str]:
    matches = [
        (domain, data["label"])
        for domain, data in DOMAINS.items()
        if table_name in data["tables"]
    ]
    if len(matches) != 1:
        raise RuntimeError(
            f"table {table_name!r} must belong to exactly one domain; matches={matches}"
        )
    return matches[0]


def load_schema(database: Database) -> dict:
    tables = database.json_rows(
        """
        SELECT json_build_object(
            'name', class.relname,
            'comment', obj_description(class.oid, 'pg_class')
        )::text
        FROM pg_class class
        JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
        WHERE namespace.nspname = 'public'
          AND class.relkind = 'r'
          AND class.relname <> 'flyway_schema_history'
        ORDER BY class.relname
        """
    )
    columns = database.json_rows(
        """
        SELECT json_build_object(
            'table', class.relname,
            'position', attribute.attnum,
            'name', attribute.attname,
            'type', format_type(attribute.atttypid, attribute.atttypmod),
            'nullable', NOT attribute.attnotnull,
            'default', pg_get_expr(default_value.adbin, default_value.adrelid),
            'generated', CASE attribute.attgenerated
                WHEN 's' THEN 'STORED'
                ELSE NULL
            END,
            'comment', col_description(class.oid, attribute.attnum)
        )::text
        FROM pg_class class
        JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
        JOIN pg_attribute attribute
          ON attribute.attrelid = class.oid
         AND attribute.attnum > 0
         AND NOT attribute.attisdropped
        LEFT JOIN pg_attrdef default_value
          ON default_value.adrelid = class.oid
         AND default_value.adnum = attribute.attnum
        WHERE namespace.nspname = 'public'
          AND class.relkind = 'r'
          AND class.relname <> 'flyway_schema_history'
        ORDER BY class.relname, attribute.attnum
        """
    )
    constraints = database.json_rows(
        """
        SELECT json_build_object(
            'table', source.relname,
            'name', constraint_info.conname,
            'type', constraint_info.contype,
            'definition', pg_get_constraintdef(constraint_info.oid, true),
            'columns', COALESCE((
                SELECT json_agg(attribute.attname ORDER BY key_position.position)
                FROM unnest(constraint_info.conkey)
                     WITH ORDINALITY key_position(attnum, position)
                JOIN pg_attribute attribute
                  ON attribute.attrelid = constraint_info.conrelid
                 AND attribute.attnum = key_position.attnum
            ), '[]'::json),
            'referencedTable', target.relname,
            'referencedColumns', COALESCE((
                SELECT json_agg(attribute.attname ORDER BY key_position.position)
                FROM unnest(constraint_info.confkey)
                     WITH ORDINALITY key_position(attnum, position)
                JOIN pg_attribute attribute
                  ON attribute.attrelid = constraint_info.confrelid
                 AND attribute.attnum = key_position.attnum
            ), '[]'::json),
            'onDelete', CASE constraint_info.contype
                WHEN 'f' THEN CASE constraint_info.confdeltype
                    WHEN 'a' THEN 'NO ACTION'
                    WHEN 'r' THEN 'RESTRICT'
                    WHEN 'c' THEN 'CASCADE'
                    WHEN 'n' THEN 'SET NULL'
                    WHEN 'd' THEN 'SET DEFAULT'
                END
                ELSE NULL
            END,
            'onUpdate', CASE constraint_info.contype
                WHEN 'f' THEN CASE constraint_info.confupdtype
                    WHEN 'a' THEN 'NO ACTION'
                    WHEN 'r' THEN 'RESTRICT'
                    WHEN 'c' THEN 'CASCADE'
                    WHEN 'n' THEN 'SET NULL'
                    WHEN 'd' THEN 'SET DEFAULT'
                END
                ELSE NULL
            END
        )::text
        FROM pg_constraint constraint_info
        JOIN pg_class source ON source.oid = constraint_info.conrelid
        JOIN pg_namespace namespace ON namespace.oid = source.relnamespace
        LEFT JOIN pg_class target ON target.oid = constraint_info.confrelid
        WHERE namespace.nspname = 'public'
          AND source.relname <> 'flyway_schema_history'
        ORDER BY source.relname, constraint_info.conname
        """
    )
    indexes = database.json_rows(
        """
        SELECT json_build_object(
            'table', class.relname,
            'name', index_class.relname,
            'primary', index_info.indisprimary,
            'unique', index_info.indisunique,
            'definition', pg_get_indexdef(index_info.indexrelid)
        )::text
        FROM pg_index index_info
        JOIN pg_class class ON class.oid = index_info.indrelid
        JOIN pg_class index_class ON index_class.oid = index_info.indexrelid
        JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
        WHERE namespace.nspname = 'public'
          AND class.relname <> 'flyway_schema_history'
        ORDER BY class.relname, index_class.relname
        """
    )

    columns_by_table: dict[str, list[dict]] = {}
    constraints_by_table: dict[str, list[dict]] = {}
    indexes_by_table: dict[str, list[dict]] = {}
    for column in columns:
        columns_by_table.setdefault(column.pop("table"), []).append(column)
    for constraint in constraints:
        constraints_by_table.setdefault(
            constraint.pop("table"), []
        ).append(constraint)
    for index in indexes:
        indexes_by_table.setdefault(index.pop("table"), []).append(index)

    foreign_keys = []
    for table in tables:
        name = table["name"]
        domain, domain_label = domain_for(name)
        table["domain"] = domain
        table["domainLabel"] = domain_label
        table["columns"] = columns_by_table.get(name, [])
        table["constraints"] = constraints_by_table.get(name, [])
        table["indexes"] = indexes_by_table.get(name, [])
        foreign_keys.extend(
            {"table": name, **constraint}
            for constraint in table["constraints"]
            if constraint["type"] == "f"
        )

    return {
        "schema": "public",
        "postgresVersion": database.scalar("SHOW server_version"),
        "flywayVersion": database.scalar(
            """
            SELECT version
            FROM flyway_schema_history
            WHERE success IS TRUE
            ORDER BY installed_rank DESC
            LIMIT 1
            """
        ),
        "tables": tables,
        "foreignKeys": foreign_keys,
    }


def write_erd(schema: dict) -> None:
    html = ERD_PATH.read_text(encoding="utf-8")
    payload = json.dumps(
        schema,
        ensure_ascii=False,
        separators=(",", ":"),
    ).replace("</", "<\\/")
    html, replaced = SCHEMA_SCRIPT.subn(
        rf"\g<1>{payload}\g<2>",
        html,
        count=1,
    )
    if replaced != 1:
        raise RuntimeError("schema-data script was not found exactly once")
    html = LEGACY_TIMETABLE_APPEND.sub("\n", html, count=1)
    ERD_PATH.write_text(html, encoding="utf-8")


def main() -> int:
    args = parse_args()
    schema = load_schema(Database(args))
    write_erd(schema)
    print(
        f"updated {ERD_PATH.relative_to(ROOT)}: "
        f"{len(schema['tables'])} tables, "
        f"{len(schema['foreignKeys'])} foreign keys, "
        f"Flyway {schema['flywayVersion']}"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERD generation failed: {error}", file=sys.stderr)
        sys.exit(1)
