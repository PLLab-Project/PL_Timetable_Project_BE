#!/usr/bin/env python3

import importlib.util
import sys
import unittest
from dataclasses import replace
from decimal import Decimal
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "extract-official-catalog.py"
SPEC = importlib.util.spec_from_file_location("official_catalog_extractor", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class OfficialCatalogExtractorTest(unittest.TestCase):

    def source_row(self, **changes):
        row = MODULE.SourceRow(
            page_number=54,
            row_number=5,
            context=MODULE.PageContext(
                "미술만화게임학부",
                "ACADEMIC_UNIT",
                "전공(미술만화게임학부)",
                None,
            ),
            course_code="825510",
            course_name="게임기초컨셉디자인",
            section_code="01",
            credits=Decimal("3"),
            lecture_hours=Decimal("4"),
            practice_hours=Decimal("4"),
            professor="박한",
            raw_lecture_time="화09:30-13:30",
            raw_location="정보408정보실습실E",
            target_grade="1학년",
            completion_category="전기",
            capacity=None,
            notes=None,
            is_shaded=True,
            raw_cells={},
        )
        return replace(row, **changes)

    def test_preserves_av_and_splits_true_multiple_rooms(self):
        self.assertEqual(
            MODULE.split_location("예314-A/V실"),
            ("예314-A/V실",),
        )
        self.assertEqual(
            MODULE.split_location("공가A113-e강의실/공가B214-일반물리실험실"),
            ("공가A113-e강의실", "공가B214-일반물리실험실"),
        )

    def test_assigns_multiple_rooms_to_one_meeting(self):
        row = self.source_row(
            raw_location="공가A113-e강의실/공가B214-일반물리실험실"
        )
        sessions, warnings = MODULE.parse_sessions(row)

        self.assertEqual(len(sessions), 1)
        self.assertEqual(len(sessions[0].rooms), 2)
        self.assertEqual(warnings, ())

    def test_allows_context_specific_completion_category(self):
        primary = self.source_row()
        cross_listed = self.source_row(
            page_number=130,
            row_number=3,
            context=MODULE.PageContext(
                "게임․콘텐츠융합전공",
                "ACADEMIC_UNIT",
                "전공(게임․콘텐츠융합전공)",
                None,
            ),
            completion_category="전선",
            notes="미술만화게임학부",
        )

        sections, contexts = MODULE.build_sections([primary, cross_listed])

        self.assertEqual(len(sections), 1)
        self.assertEqual([is_primary for _, is_primary in contexts], [True, False])

    def test_rejects_conflicting_duplicate_core_values(self):
        with self.assertRaisesRegex(ValueError, "conflicting official core"):
            MODULE.build_sections(
                [self.source_row(), self.source_row(professor="다른교수")]
            )

    def test_derives_liberal_area_category(self):
        context = MODULE.page_context(
            ["교양 선택 과목", "3영역:과학과기술"]
        )

        self.assertEqual(context.kind, "GENERAL_EDUCATION_AREA")
        self.assertEqual(context.course_category, "교양선택(제3영역:과학과기술)")
        self.assertEqual(context.default_completion_category, "교선")

    def test_preserves_unresolved_micro_major_source_claim(self):
        listing = MODULE.ProgramCourseRow(
            page_number=141,
            row_number=1,
            college_name="공과대학",
            program_name="반도체디스플레이소재부품",
            course_name="반도체소자공정",
            offering_unit_label="반도체융합공학과",
            catalog_page_label="107",
            is_offered=True,
            resolution_status="SOURCE_NOT_FOUND",
            course_code=None,
            raw_cells={},
        )

        resolved = MODULE.resolve_program_courses([listing], [self.source_row()])

        self.assertEqual(resolved[0].resolution_status, "SOURCE_NOT_FOUND")
        self.assertIsNone(resolved[0].course_code)

    def test_resolves_micro_major_course_with_punctuation_difference(self):
        source = self.source_row(
            course_code="992001",
            course_name="산업보안법․정책",
            context=MODULE.PageContext(
                "스마트융합보안학과",
                "ACADEMIC_UNIT",
                "전공(스마트융합보안학과)",
                None,
            ),
        )
        listing = MODULE.ProgramCourseRow(
            page_number=140,
            row_number=1,
            college_name="AI융합대학",
            program_name="시스템보안",
            course_name="산업보안법·정책",
            offering_unit_label="스마트융합보안학과",
            catalog_page_label="96",
            is_offered=True,
            resolution_status="SOURCE_NOT_FOUND",
            course_code=None,
            raw_cells={},
        )

        resolved = MODULE.resolve_program_courses([listing], [source])

        self.assertEqual(resolved[0].resolution_status, "RESOLVED")
        self.assertEqual(resolved[0].course_code, "992001")


if __name__ == "__main__":
    unittest.main()
