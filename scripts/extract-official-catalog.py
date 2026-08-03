#!/usr/bin/env python3
"""Extract an official Daejin University course-table PDF into loadable SQL.

The canonical tables contain one row per course/section/session. Every source
row is also retained so duplicate cross-listing contexts and the exact PDF
cells remain auditable.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import sys
import uuid
from collections import Counter
from dataclasses import dataclass, replace
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable


PARSER_VERSION = "official-catalog-pdf-v2"
DATASET_VERSION = "official-pdf-v2"
COURSE_CODE_PATTERN = re.compile(r"^\d{6}$")
SECTION_CODE_PATTERN = re.compile(r"^\d{2}$")
TIME_PATTERN = re.compile(
    r"([월화수목금토일])(\d{2}):(\d{2})-(\d{2}):(\d{2})"
)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_HEADERS = {"교과번호", "교과목명", "분반", "강의시간"}
PROGRAM_COURSE_HEADERS = {"단과대학", "전공명", "교과목명", "개설학과", "페이지"}


@dataclass(frozen=True)
class PageContext:
    label: str
    kind: str
    course_category: str
    default_completion_category: str | None


@dataclass(frozen=True)
class SourceRow:
    page_number: int
    row_number: int
    context: PageContext
    course_code: str
    course_name: str
    section_code: str
    credits: Decimal
    lecture_hours: Decimal
    practice_hours: Decimal
    professor: str | None
    raw_lecture_time: str
    raw_location: str | None
    target_grade: str | None
    completion_category: str | None
    capacity: int | None
    notes: str | None
    is_shaded: bool
    raw_cells: dict[str, str | None]


@dataclass(frozen=True)
class ProgramCourseRow:
    page_number: int
    row_number: int
    college_name: str
    program_name: str
    course_name: str
    offering_unit_label: str
    catalog_page_label: str
    is_offered: bool
    resolution_status: str
    course_code: str | None
    raw_cells: dict[str, str | None]


@dataclass(frozen=True)
class Session:
    sequence_no: int
    day: str
    start_minute: int
    end_minute: int
    rooms: tuple[str, ...]


@dataclass(frozen=True)
class Section:
    source: SourceRow
    sessions: tuple[Session, ...]
    warning_codes: tuple[str, ...]


def compact_header(value: str | None) -> str:
    return re.sub(r"\s+", "", value or "")


def clean_identifier(value: str | None) -> str:
    return re.sub(r"\s+", "", value or "")


def clean_name(value: str | None) -> str:
    cleaned = re.sub(r"\s+", "", value or "").strip()
    return re.sub(r"^\*+", "", cleaned)


def clean_compact(value: str | None) -> str | None:
    cleaned = re.sub(r"\s+", "", value or "").strip()
    return cleaned or None


def clean_text(value: str | None) -> str | None:
    cleaned = re.sub(r"\s+", " ", value or "").strip()
    return cleaned or None


def clean_notes(value: str | None) -> str | None:
    # pdfplumber inserts line breaks when text wraps inside a table cell. Those
    # wraps can split Korean words (for example, "우\n선수강"), so join only
    # physical lines and preserve intentional spaces that existed on one line.
    joined_lines = re.sub(
        r"[^\S\r\n]*(?:\r\n?|\n)+[^\S\r\n]*", "", value or ""
    )
    cleaned = re.sub(r"[^\S\r\n]+", " ", joined_lines).strip()
    return cleaned or None


def parse_decimal(value: str | None, field: str, locator: str) -> Decimal:
    cleaned = clean_identifier(value)
    try:
        result = Decimal(cleaned)
    except InvalidOperation as exception:
        raise ValueError(f"{locator}: invalid {field}: {value!r}") from exception
    if result < 0:
        raise ValueError(f"{locator}: negative {field}: {value!r}")
    return result


def parse_capacity(value: str | None, locator: str) -> int | None:
    cleaned = clean_identifier(value)
    if not cleaned:
        return None
    if not cleaned.isdigit():
        raise ValueError(f"{locator}: invalid capacity: {value!r}")
    return int(cleaned)


def normalize_grade(value: str | None) -> str | None:
    cleaned = clean_text(value)
    if not cleaned:
        return None
    if cleaned.isdigit():
        return f"{cleaned}학년"
    return cleaned


def page_context(lines: list[str]) -> PageContext:
    if not lines:
        raise ValueError("course-table page has no title")
    title = clean_text(lines[0]) or ""
    if title == "교양 필수 과목":
        return PageContext(title, "GENERAL_EDUCATION", "교양필수", "교필")
    if title == "교양 선택 과목":
        if len(lines) < 2:
            raise ValueError("liberal elective page has no area title")
        area = re.sub(r"\s+", "", lines[1])
        display_area = area if area.startswith("제") else f"제{area}"
        return PageContext(
            f"{title} / {area}",
            "GENERAL_EDUCATION_AREA",
            f"교양선택({display_area})",
            "교선",
        )
    if title == "교직 과목":
        return PageContext(title, "TEACHING", "교직", "교직")
    if title == "일반 선택 과목":
        return PageContext(title, "GENERAL_ELECTIVE", "일반선택", "일선")
    return PageContext(title, "ACADEMIC_UNIT", f"전공({title})", None)


def is_course_table(headers: list[str]) -> bool:
    return REQUIRED_HEADERS.issubset(set(headers))


def is_program_course_table(headers: list[str]) -> bool:
    return PROGRAM_COURSE_HEADERS.issubset(set(headers))


def row_is_shaded(page: Any, cell: tuple[float, float, float, float] | None) -> bool:
    if cell is None:
        return False
    x0, top, x1, bottom = cell
    for rect in page.rects:
        color = rect.get("non_stroking_color")
        if not isinstance(color, (int, float)) or abs(float(color) - 0.851) > 0.002:
            continue
        overlap_width = min(x1, rect["x1"]) - max(x0, rect["x0"])
        overlap_height = min(bottom, rect["bottom"]) - max(top, rect["top"])
        if overlap_width > 0.5 and overlap_height > 0.5:
            return True
    return False


def extract_rows(
    pdf_path: Path,
) -> tuple[list[SourceRow], list[ProgramCourseRow], dict[str, Any]]:
    try:
        import pdfplumber
    except ImportError as exception:
        raise RuntimeError(
            "pdfplumber is required; install scripts/requirements-academic-data.txt"
        ) from exception

    rows: list[SourceRow] = []
    program_rows: list[ProgramCourseRow] = []
    course_page_count = 0
    program_page_count = 0
    with pdfplumber.open(pdf_path) as document:
        for page_number, page in enumerate(document.pages, start=1):
            lines = (page.extract_text() or "").splitlines()
            page_row_number = 0
            for table in page.find_tables():
                extracted = table.extract()
                if not extracted:
                    continue
                headers = [compact_header(cell) for cell in extracted[0]]
                if is_program_course_table(headers):
                    program_page_count += 1
                    inherited: dict[str, str | None] = {
                        "단과대학": None,
                        "전공명": None,
                        "개설학과": None,
                    }
                    for program_row_number, raw_values in enumerate(
                        extracted[1:], start=1
                    ):
                        values = list(raw_values) + [None] * (
                            len(headers) - len(raw_values)
                        )
                        raw_cells = {
                            header: values[index]
                            for index, header in enumerate(headers)
                            if header
                        }
                        for header in inherited:
                            cleaned = clean_text(raw_cells.get(header))
                            if cleaned:
                                inherited[header] = cleaned
                        course_name = clean_name(raw_cells.get("교과목명"))
                        page_label = clean_identifier(raw_cells.get("페이지"))
                        locator = (
                            f"page {page_number}, program row {program_row_number}"
                        )
                        if not course_name:
                            raise ValueError(
                                f"{locator}: missing micro-major course name"
                            )
                        if page_label != "미개설" and not page_label.isdigit():
                            raise ValueError(
                                f"{locator}: invalid catalog page {page_label!r}"
                            )
                        if any(value is None for value in inherited.values()):
                            raise ValueError(
                                f"{locator}: incomplete micro-major context"
                            )
                        program_rows.append(
                            ProgramCourseRow(
                                page_number=page_number,
                                row_number=program_row_number,
                                college_name=inherited["단과대학"] or "",
                                program_name=inherited["전공명"] or "",
                                course_name=course_name,
                                offering_unit_label=inherited["개설학과"] or "",
                                catalog_page_label=page_label,
                                is_offered=page_label != "미개설",
                                resolution_status=(
                                    "SOURCE_NOT_FOUND"
                                    if page_label != "미개설"
                                    else "NOT_OFFERED"
                                ),
                                course_code=None,
                                raw_cells=raw_cells,
                            )
                        )
                    continue
                if not is_course_table(headers):
                    continue
                course_page_count += 1
                context = page_context(lines)
                indexes = {header: index for index, header in enumerate(headers)}
                inherited_grade: str | None = None
                inherited_category: str | None = context.default_completion_category
                active_course: dict[str, Any] | None = None
                for table_row_number, raw_values in enumerate(extracted[1:], start=1):
                    page_row_number += 1
                    values = list(raw_values) + [None] * (len(headers) - len(raw_values))
                    raw_cells = {
                        header: values[index]
                        for index, header in enumerate(headers)
                        if header
                    }
                    raw_course_code = raw_cells.get("교과번호")
                    raw_section_code = raw_cells.get("분반")
                    section_code = clean_identifier(raw_section_code)
                    if not section_code:
                        continue
                    locator = f"page {page_number}, row {page_row_number}"
                    if not SECTION_CODE_PATTERN.fullmatch(section_code):
                        raise ValueError(
                            f"{locator}: invalid section code: {raw_section_code!r}"
                        )

                    raw_grade = raw_cells.get("학년")
                    if clean_text(raw_grade):
                        inherited_grade = normalize_grade(raw_grade)
                    raw_category = raw_cells.get("이수구분")
                    if clean_text(raw_category):
                        inherited_category = clean_compact(raw_category)

                    course_code = clean_identifier(raw_course_code)
                    new_course = bool(course_code)
                    if new_course:
                        if not COURSE_CODE_PATTERN.fullmatch(course_code):
                            raise ValueError(
                                f"{locator}: invalid course code: {raw_course_code!r}"
                            )
                        course_name = clean_name(raw_cells.get("교과목명"))
                        if not course_name:
                            raise ValueError(f"{locator}: missing course name")
                        code_cell = table.rows[table_row_number].cells[
                            indexes["교과번호"]
                        ]
                        active_course = {
                            "course_code": course_code,
                            "course_name": course_name,
                            "professor": clean_text(raw_cells.get("담당교수")),
                            "raw_location": clean_compact(raw_cells.get("강의실")),
                            "notes": clean_notes(raw_cells.get("비고")),
                            "is_shaded": row_is_shaded(page, code_cell),
                        }
                    elif active_course is None:
                        raise ValueError(f"{locator}: section appears before course code")

                    assert active_course is not None
                    for inherited_field, header in (
                        ("professor", "담당교수"),
                        ("raw_location", "강의실"),
                        ("notes", "비고"),
                    ):
                        raw_value = raw_cells.get(header)
                        if header == "강의실":
                            cleaned = clean_compact(raw_value)
                        elif header == "비고":
                            cleaned = clean_notes(raw_value)
                        else:
                            cleaned = clean_text(raw_value)
                        if cleaned is not None:
                            active_course[inherited_field] = cleaned

                    rows.append(
                        SourceRow(
                            page_number=page_number,
                            row_number=page_row_number,
                            context=context,
                            course_code=active_course["course_code"],
                            course_name=active_course["course_name"],
                            section_code=section_code,
                            credits=parse_decimal(
                                raw_cells.get("학점"), "credits", locator
                            ),
                            lecture_hours=parse_decimal(
                                raw_cells.get("강의"), "lecture hours", locator
                            ),
                            practice_hours=parse_decimal(
                                raw_cells.get("실습"), "practice hours", locator
                            ),
                            professor=active_course["professor"],
                            raw_lecture_time=clean_compact(
                                raw_cells.get("강의시간")
                            )
                            or "",
                            raw_location=active_course["raw_location"],
                            target_grade=inherited_grade,
                            completion_category=inherited_category,
                            capacity=parse_capacity(raw_cells.get("정원"), locator),
                            notes=active_course["notes"],
                            is_shaded=active_course["is_shaded"],
                            raw_cells=raw_cells,
                        )
                    )
        metadata = {
            "pageCount": len(document.pages),
            "coursePageCount": course_page_count,
            "programPageCount": program_page_count,
            "pdfMetadata": {
                key: str(value)
                for key, value in (document.metadata or {}).items()
                if value is not None
            },
        }
    program_rows = resolve_program_courses(program_rows, rows)
    return rows, program_rows, metadata


def resolve_program_courses(
    program_rows: list[ProgramCourseRow],
    source_rows: list[SourceRow],
) -> list[ProgramCourseRow]:
    by_page_and_name: dict[tuple[int, str], set[str]] = {}
    by_name_and_context: dict[tuple[str, str], set[str]] = {}
    by_name: dict[str, set[str]] = {}
    for row in source_rows:
        name = normalize_lookup_key(row.course_name)
        by_page_and_name.setdefault((row.page_number, name), set()).add(
            row.course_code
        )
        context_key = normalize_lookup_key(row.context.label)
        by_name_and_context.setdefault((name, context_key), set()).add(
            row.course_code
        )
        by_name.setdefault(name, set()).add(row.course_code)

    resolved: list[ProgramCourseRow] = []
    for row in program_rows:
        if not row.is_offered:
            resolved.append(row)
            continue
        printed_page = int(row.catalog_page_label)
        name = normalize_lookup_key(row.course_name)
        candidates = by_name_and_context.get(
            (name, normalize_lookup_key(row.offering_unit_label)),
            set(),
        )
        if not candidates:
            candidates = set()
            for page_offset in (2, 3):
                candidates.update(
                    by_page_and_name.get((printed_page + page_offset, name), set())
                )
        if not candidates:
            candidates = by_name.get(name, set())
        if len(candidates) == 1:
            resolved.append(
                replace(
                    row,
                    resolution_status="RESOLVED",
                    course_code=next(iter(candidates)),
                )
            )
        elif candidates:
            resolved.append(replace(row, resolution_status="AMBIGUOUS"))
        else:
            resolved.append(row)
    return resolved


def normalize_lookup_key(value: str) -> str:
    return re.sub(r"[^0-9A-Za-z가-힣]+", "", value).lower()


def split_location(value: str | None) -> tuple[str, ...]:
    if not value:
        return ()
    chunks = re.split(r"(?<!A)/(?!V)|,(?=[가-힣A-Z])", value)
    result: list[str] = []
    for chunk in chunks:
        cleaned = clean_compact(chunk)
        if cleaned and cleaned not in result:
            result.append(cleaned)
    return tuple(result)


def parse_sessions(row: SourceRow) -> tuple[tuple[Session, ...], tuple[str, ...]]:
    if not row.raw_lecture_time:
        return (), ()
    matches = list(TIME_PATTERN.finditer(row.raw_lecture_time))
    reconstructed = ",".join(match.group(0) for match in matches)
    if not matches or reconstructed != row.raw_lecture_time:
        raise ValueError(
            f"page {row.page_number}, row {row.row_number}: "
            f"unparsed lecture time {row.raw_lecture_time!r}"
        )

    room_values = split_location(row.raw_location)
    warnings: list[str] = []
    if not room_values:
        room_groups = [()] * len(matches)
    elif len(room_values) == len(matches):
        room_groups = [(room,) for room in room_values]
    elif len(matches) == 1:
        room_groups = [room_values]
    elif len(room_values) == 1:
        room_groups = [room_values] * len(matches)
    else:
        room_groups = [room_values] * len(matches)
        warnings.append("ROOM_MAPPING_AMBIGUOUS")

    sessions: list[Session] = []
    for sequence_no, (match, rooms) in enumerate(
        zip(matches, room_groups, strict=True), start=1
    ):
        start_minute = int(match.group(2)) * 60 + int(match.group(3))
        end_minute = int(match.group(4)) * 60 + int(match.group(5))
        if start_minute >= end_minute or end_minute > 1440:
            raise ValueError(
                f"page {row.page_number}, row {row.row_number}: "
                f"invalid lecture time {match.group(0)!r}"
            )
        sessions.append(
            Session(
                sequence_no=sequence_no,
                day=match.group(1),
                start_minute=start_minute,
                end_minute=end_minute,
                rooms=rooms,
            )
        )
    return tuple(sessions), tuple(warnings)


def normalized_section_signature(row: SourceRow) -> tuple[Any, ...]:
    return (
        row.course_name,
        row.credits,
        row.lecture_hours,
        row.practice_hours,
        row.professor,
        row.raw_lecture_time,
        row.raw_location,
    )


def build_sections(
    source_rows: list[SourceRow],
) -> tuple[list[Section], list[tuple[SourceRow, bool]]]:
    canonical: dict[tuple[str, str], Section] = {}
    contexts: list[tuple[SourceRow, bool]] = []
    for row in source_rows:
        key = (row.course_code, row.section_code)
        primary = key not in canonical
        contexts.append((row, primary))
        if primary:
            sessions, warnings = parse_sessions(row)
            canonical[key] = Section(row, sessions, warnings)
            continue
        previous = canonical[key].source
        if normalized_section_signature(row) != normalized_section_signature(previous):
            raise ValueError(
                "duplicate section has conflicting official core values: "
                f"{row.course_code}-{row.section_code} "
                f"(pages {previous.page_number}, {row.page_number})"
            )
    return list(canonical.values()), contexts


def verify_course_consistency(sections: Iterable[Section]) -> None:
    courses: dict[str, tuple[Any, ...]] = {}
    for section in sections:
        source = section.source
        signature = (
            source.course_name,
            source.credits,
            source.lecture_hours,
            source.practice_hours,
        )
        previous = courses.setdefault(source.course_code, signature)
        if previous != signature:
            raise ValueError(
                f"course-level values differ between sections: {source.course_code}"
            )


def room_code(label: str) -> str:
    digest = hashlib.sha1(label.encode("utf-8")).hexdigest()[:20].upper()
    return f"OFF-{digest}"


def copy_escape(value: Any) -> str:
    if value is None:
        return r"\N"
    if isinstance(value, bool):
        return "t" if value else "f"
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, (dict, list, tuple)):
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    text = str(value)
    return (
        text.replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    )


def copy_block(
    output: list[str], table: str, columns: tuple[str, ...], rows: Iterable[Iterable[Any]]
) -> None:
    output.append(f"COPY {table} ({', '.join(columns)}) FROM stdin;")
    for row in rows:
        output.append("\t".join(copy_escape(value) for value in row))
    output.append(r"\.")


def source_snapshot(row: SourceRow) -> dict[str, Any]:
    return {
        "page": row.page_number,
        "row": row.row_number,
        "context": row.context.label,
        "courseCode": row.course_code,
        "courseName": row.course_name,
        "sectionCode": row.section_code,
        "credits": float(row.credits),
        "lectureHours": float(row.lecture_hours),
        "practiceHours": float(row.practice_hours),
        "professor": row.professor,
        "lectureTime": row.raw_lecture_time,
        "location": row.raw_location,
        "targetGrade": row.target_grade,
        "completionCategory": row.completion_category,
        "capacity": row.capacity,
        "notes": row.notes,
        "shaded": row.is_shaded,
    }


def generate_sql(
    pdf_path: Path,
    semester_id: str,
    prepared_on: date,
    checksum: str,
    source_rows: list[SourceRow],
    program_rows: list[ProgramCourseRow],
    sections: list[Section],
    contexts: list[tuple[SourceRow, bool]],
    metadata: dict[str, Any],
) -> tuple[str, dict[str, Any]]:
    courses: dict[str, SourceRow] = {}
    rooms: dict[str, str] = {}
    session_room_rows: list[tuple[Any, ...]] = []
    for section in sections:
        source = section.source
        courses.setdefault(source.course_code, source)
        for session in section.sessions:
            for position, label in enumerate(session.rooms, start=1):
                code = room_code(label)
                rooms[code] = label
                session_room_rows.append(
                    (
                        source.course_code,
                        source.section_code,
                        session.sequence_no,
                        code,
                        position,
                    )
                )

    tba_count = sum(not section.sessions for section in sections)
    duplicate_count = len(source_rows) - len(sections)
    shaded_count = sum(row.is_shaded for row in source_rows)
    ambiguous_room_count = sum(
        "ROOM_MAPPING_AMBIGUOUS" in section.warning_codes for section in sections
    )
    report = {
        "semester": semester_id,
        "parserVersion": PARSER_VERSION,
        "sourceChecksum": checksum,
        "datasetVersion": f"{DATASET_VERSION}-{checksum[:12]}",
        "rawRows": len(source_rows),
        "microMajorRows": len(program_rows),
        "microMajorOfferedRows": sum(row.is_offered for row in program_rows),
        "microMajorUnopenedRows": sum(not row.is_offered for row in program_rows),
        "microMajorResolvedRows": sum(
            row.resolution_status == "RESOLVED" for row in program_rows
        ),
        "microMajorSourceNotFoundRows": sum(
            row.resolution_status == "SOURCE_NOT_FOUND" for row in program_rows
        ),
        "microMajorAmbiguousRows": sum(
            row.resolution_status == "AMBIGUOUS" for row in program_rows
        ),
        "courses": len(courses),
        "sections": len(sections),
        "duplicateContextRows": duplicate_count,
        "sessions": sum(len(section.sessions) for section in sections),
        "rooms": len(rooms),
        "sessionRoomLinks": len(session_room_rows),
        "timeToBeAnnounced": tba_count,
        "shadedRows": shaded_count,
        "ambiguousRoomMappings": ambiguous_room_count,
        **metadata,
    }
    import_id = str(
        uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"catalog:{semester_id}:{checksum}:{PARSER_VERSION}",
        )
    )
    generated_at = f"{prepared_on.isoformat()}T00:00:00+00:00"
    original_name = pdf_path.name

    sql: list[str] = [
        r"\set ON_ERROR_STOP on",
        "BEGIN;",
        f"DELETE FROM data_imports WHERE semester_id = {sql_literal(semester_id)};",
        f"DELETE FROM catalog_sources WHERE semester_id = {sql_literal(semester_id)};",
        f"DELETE FROM courses WHERE semester_id = {sql_literal(semester_id)};",
        f"DELETE FROM rooms WHERE semester_id = {sql_literal(semester_id)};",
        (
            "INSERT INTO semesters "
            "(id, prepared_at, dataset_version, source_checksum, is_active, created_at) "
            f"VALUES ({sql_literal(semester_id)}, {sql_literal(prepared_on.isoformat())}::date, "
            f"{sql_literal(report['datasetVersion'])}, {sql_literal(checksum)}, TRUE, "
            f"{sql_literal(generated_at)}::timestamptz) "
            "ON CONFLICT (id) DO UPDATE SET "
            "prepared_at = EXCLUDED.prepared_at, "
            "dataset_version = EXCLUDED.dataset_version, "
            "source_checksum = EXCLUDED.source_checksum, "
            "is_active = TRUE;"
        ),
    ]

    copy_block(
        sql,
        "courses",
        (
            "semester_id",
            "course_code",
            "name",
            "category",
            "credits",
            "lecture_hours",
            "practice_hours",
        ),
        (
            (
                semester_id,
                row.course_code,
                row.course_name,
                row.context.course_category,
                row.credits,
                row.lecture_hours,
                row.practice_hours,
            )
            for row in sorted(courses.values(), key=lambda item: item.course_code)
        ),
    )
    copy_block(
        sql,
        "sections",
        (
            "semester_id",
            "course_code",
            "section_code",
            "professor",
            "raw_lecture_time",
            "time_to_be_announced",
            "warning_codes",
            "target_grade",
            "capacity",
            "notes",
            "raw_location",
            "source_page",
            "source_row",
            "source_snapshot",
        ),
        (
            (
                semester_id,
                section.source.course_code,
                section.source.section_code,
                section.source.professor,
                section.source.raw_lecture_time,
                not section.sessions,
                list(section.warning_codes),
                section.source.target_grade,
                section.source.capacity,
                section.source.notes,
                section.source.raw_location,
                section.source.page_number,
                section.source.row_number,
                source_snapshot(section.source),
            )
            for section in sorted(
                sections, key=lambda item: (item.source.course_code, item.source.section_code)
            )
        ),
    )
    copy_block(
        sql,
        "rooms",
        (
            "semester_id",
            "code",
            "building_code",
            "building_name",
            "label",
            "room_type",
            "capacity",
        ),
        (
            (semester_id, code, None, None, label, "OFFICIAL_PDF", None)
            for code, label in sorted(rooms.items())
        ),
    )
    copy_block(
        sql,
        "sessions",
        (
            "semester_id",
            "course_code",
            "section_code",
            "day",
            "start_minute",
            "end_minute",
            "room_code",
            "sequence_no",
        ),
        (
            (
                semester_id,
                section.source.course_code,
                section.source.section_code,
                session.day,
                session.start_minute,
                session.end_minute,
                room_code(session.rooms[0]) if session.rooms else None,
                session.sequence_no,
            )
            for section in sorted(
                sections, key=lambda item: (item.source.course_code, item.source.section_code)
            )
            for session in section.sessions
        ),
    )
    sql.extend(
        [
            "CREATE TEMP TABLE catalog_session_room_stage ("
            "course_code varchar(40), section_code varchar(20), "
            "sequence_no smallint, room_code varchar(40), position smallint"
            ") ON COMMIT DROP;"
        ]
    )
    copy_block(
        sql,
        "catalog_session_room_stage",
        ("course_code", "section_code", "sequence_no", "room_code", "position"),
        sorted(session_room_rows),
    )
    sql.extend(
        [
            "INSERT INTO session_rooms (session_id, semester_id, room_code, position)",
            "SELECT sessions.id, sessions.semester_id, stage.room_code, stage.position",
            "FROM catalog_session_room_stage stage",
            "JOIN sessions",
            f"  ON sessions.semester_id = {sql_literal(semester_id)}",
            " AND sessions.course_code = stage.course_code",
            " AND sessions.section_code = stage.section_code",
            " AND sessions.sequence_no = stage.sequence_no;",
            (
                "INSERT INTO catalog_sources "
                "(checksum, semester_id, source_kind, original_file_name, published_on, "
                "parser_version, raw_row_count, supplemental_row_count, "
                "unique_section_count, imported_at, metadata) "
                f"VALUES ({sql_literal(checksum)}, {sql_literal(semester_id)}, "
                f"'OFFICIAL_PDF', {sql_literal(original_name)}, "
                f"{sql_literal(prepared_on.isoformat())}::date, "
                f"{sql_literal(PARSER_VERSION)}, {len(source_rows)}, "
                f"{len(program_rows)}, {len(sections)}, "
                f"{sql_literal(generated_at)}::timestamptz, "
                f"{sql_literal(json.dumps(report, ensure_ascii=False, separators=(',', ':')))}::jsonb);"
            ),
        ]
    )
    copy_block(
        sql,
        "catalog_source_rows",
        (
            "source_checksum",
            "semester_id",
            "course_code",
            "section_code",
            "page_number",
            "row_number",
            "context_label",
            "is_shaded",
            "raw_cells",
        ),
        (
            (
                checksum,
                semester_id,
                row.course_code,
                row.section_code,
                row.page_number,
                row.row_number,
                row.context.label,
                row.is_shaded,
                row.raw_cells,
            )
            for row in source_rows
        ),
    )
    copy_block(
        sql,
        "catalog_program_course_listings",
        (
            "source_checksum",
            "semester_id",
            "program_kind",
            "college_name",
            "program_name",
            "course_name",
            "offering_unit_label",
            "offering_academic_unit_code",
            "catalog_page_label",
            "is_offered",
            "resolution_status",
            "course_code",
            "source_page",
            "source_row",
            "raw_cells",
        ),
        (
            (
                checksum,
                semester_id,
                "MICRO_MAJOR",
                row.college_name,
                row.program_name,
                row.course_name,
                row.offering_unit_label,
                None,
                row.catalog_page_label,
                row.is_offered,
                row.resolution_status,
                row.course_code,
                row.page_number,
                row.row_number,
                row.raw_cells,
            )
            for row in program_rows
        ),
    )
    copy_block(
        sql,
        "section_classification_contexts",
        (
            "semester_id",
            "course_code",
            "section_code",
            "source_checksum",
            "context_label",
            "context_kind",
            "academic_unit_code",
            "completion_category",
            "target_grade",
            "is_primary",
            "is_shaded",
            "source_page",
            "source_row",
        ),
        (
            (
                semester_id,
                row.course_code,
                row.section_code,
                checksum,
                row.context.label,
                row.context.kind,
                None,
                row.completion_category,
                row.target_grade,
                primary,
                row.is_shaded,
                row.page_number,
                row.row_number,
            )
            for row, primary in contexts
        ),
    )
    sql.extend(
        [
            "UPDATE section_classification_contexts context",
            "SET academic_unit_code = (",
            "    SELECT candidate.code",
            "    FROM (",
            "        SELECT units.code, 0 AS priority",
            "        FROM academic_units units",
            "        WHERE units.normalized_key = "
            "normalize_academic_unit_key(context.context_label)",
            "        UNION ALL",
            "        SELECT aliases.academic_unit_code, 1 AS priority",
            "        FROM academic_unit_aliases aliases",
            "        WHERE aliases.alias_key = "
            "normalize_academic_unit_key(context.context_label)",
            "          AND (aliases.valid_from_year IS NULL "
            f"OR aliases.valid_from_year <= {prepared_on.year})",
            "          AND (aliases.valid_to_year IS NULL "
            f"OR aliases.valid_to_year >= {prepared_on.year})",
            "    ) candidate",
            "    ORDER BY candidate.priority, candidate.code",
            "    LIMIT 1",
            ")",
            f"WHERE context.source_checksum = {sql_literal(checksum)}",
            "  AND context.context_kind = 'ACADEMIC_UNIT';",
            "UPDATE catalog_program_course_listings listing",
            "SET offering_academic_unit_code = (",
            "    SELECT candidate.code",
            "    FROM (",
            "        SELECT units.code, 0 AS priority",
            "        FROM academic_units units",
            "        WHERE units.normalized_key = ",
            "              normalize_academic_unit_key(listing.offering_unit_label)",
            "        UNION ALL",
            "        SELECT aliases.academic_unit_code, 1 AS priority",
            "        FROM academic_unit_aliases aliases",
            "        WHERE aliases.alias_key = ",
            "              normalize_academic_unit_key(listing.offering_unit_label)",
            "          AND (aliases.valid_from_year IS NULL "
            f"OR aliases.valid_from_year <= {prepared_on.year})",
            "          AND (aliases.valid_to_year IS NULL "
            f"OR aliases.valid_to_year >= {prepared_on.year})",
            "    ) candidate",
            "    ORDER BY candidate.priority, candidate.code",
            "    LIMIT 1",
            ")",
            f"WHERE listing.source_checksum = {sql_literal(checksum)};",
            (
                "INSERT INTO section_academic_units "
                "(semester_id, course_code, section_code, academic_unit_code, "
                "relation_type, source_kind)"
            ),
            "SELECT semester_id, course_code, section_code, academic_unit_code,",
            "       CASE WHEN is_primary THEN 'OFFERING' ELSE 'CROSS_LISTED' END,",
            "       'OFFICIAL_CATALOG_PDF'",
            "FROM section_classification_contexts",
            f"WHERE source_checksum = {sql_literal(checksum)}",
            "  AND academic_unit_code IS NOT NULL",
            "ON CONFLICT DO NOTHING;",
            (
                "INSERT INTO data_imports "
                "(id, semester_id, checksum, parser_version, status, report, created_at) "
                f"VALUES ({sql_literal(import_id)}, {sql_literal(semester_id)}, "
                f"{sql_literal(checksum)}, {sql_literal(PARSER_VERSION)}, 'SUCCEEDED', "
                f"{sql_literal(json.dumps(report, ensure_ascii=False, separators=(',', ':')))}::jsonb, "
                f"{sql_literal(generated_at)}::timestamptz);"
            ),
            "COMMIT;",
            "",
        ]
    )
    return "\n".join(sql), report


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--semester", default="2026-2")
    parser.add_argument("--prepared-on", type=date.fromisoformat, default=date(2026, 7, 24))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    pdf_path = args.pdf.resolve()
    if not pdf_path.is_file():
        raise FileNotFoundError(pdf_path)
    checksum = hashlib.sha256(pdf_path.read_bytes()).hexdigest()
    if not SHA256_PATTERN.fullmatch(checksum):
        raise AssertionError("invalid SHA-256")

    source_rows, program_rows, metadata = extract_rows(pdf_path)
    sections, contexts = build_sections(source_rows)
    verify_course_consistency(sections)
    sql, report = generate_sql(
        pdf_path,
        args.semester,
        args.prepared_on,
        checksum,
        source_rows,
        program_rows,
        sections,
        contexts,
        metadata,
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as raw_output:
        with gzip.GzipFile(
            filename="",
            mode="wb",
            fileobj=raw_output,
            compresslevel=9,
            mtime=0,
        ) as compressed_output:
            compressed_output.write(sql.encode("utf-8"))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exception:
        print(f"official catalog extraction failed: {exception}", file=sys.stderr)
        raise
