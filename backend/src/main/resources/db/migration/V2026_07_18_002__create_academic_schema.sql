--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: courses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.courses (
    semester_id character varying(20) NOT NULL,
    course_code character varying(40) NOT NULL,
    name text NOT NULL,
    category text NOT NULL,
    credits numeric(5,2) NOT NULL
);


--
-- Name: curriculum_program_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_program_aliases (
    id character varying(36) NOT NULL,
    program_id character varying(36) NOT NULL,
    admission_year integer NOT NULL,
    alias character varying(240) NOT NULL,
    alias_key character varying(240) NOT NULL,
    is_primary boolean NOT NULL
);


--
-- Name: curriculum_program_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_program_requirements (
    id character varying(36) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    admission_year integer NOT NULL,
    academic_unit character varying(240) NOT NULL,
    academic_unit_key character varying(240) NOT NULL,
    status character varying(24) NOT NULL,
    source_locators json NOT NULL,
    source_course_count integer NOT NULL,
    required_course_count integer NOT NULL,
    raw_payload json NOT NULL,
    CONSTRAINT ck_curriculum_program_admission_year CHECK (((admission_year >= 1900) AND (admission_year <= 2100))),
    CONSTRAINT ck_curriculum_program_course_counts CHECK (((source_course_count >= 0) AND (required_course_count >= 0)))
);


--
-- Name: curriculum_required_courses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_required_courses (
    id character varying(36) NOT NULL,
    program_id character varying(36) NOT NULL,
    classification character varying(20) NOT NULL,
    course_code character varying(40) NOT NULL,
    course_name character varying(240) NOT NULL,
    credits double precision,
    grade integer,
    semesters json NOT NULL,
    source_locator json NOT NULL,
    raw_payload json NOT NULL,
    CONSTRAINT ck_curriculum_required_course_classification CHECK (((classification)::text = ANY ((ARRAY['전기'::character varying, '전필'::character varying])::text[]))),
    CONSTRAINT ck_required_course_credits CHECK (((credits IS NULL) OR (credits >= (0)::double precision))),
    CONSTRAINT ck_required_course_grade CHECK (((grade IS NULL) OR ((grade >= 1) AND (grade <= 6))))
);


--
-- Name: data_imports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_imports (
    id character varying(36) NOT NULL,
    semester_id character varying(20) NOT NULL,
    checksum character varying(64) NOT NULL,
    parser_version character varying(40) NOT NULL,
    status character varying(24) NOT NULL,
    report jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: graduation_assessment_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_assessment_categories (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    category_code character varying(1) NOT NULL,
    category_name character varying(240) NOT NULL,
    primary_none text,
    primary_one text,
    primary_two text,
    double_major_none text,
    double_major_one text,
    requirement_detail text,
    reference_note text,
    source_note text,
    CONSTRAINT ck_graduation_assessment_category_code CHECK (((category_code)::text = ANY ((ARRAY['A'::character varying, 'C'::character varying, 'E'::character varying, 'S'::character varying])::text[])))
);


--
-- Name: graduation_assessment_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_assessment_credentials (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    international_or_national_certification text,
    private_or_other_certification text,
    foreign_language text,
    awards text,
    employment_or_experience text,
    double_major_requirement text,
    reference_note text,
    source_note text,
    CONSTRAINT ck_graduation_assessment_credential_position CHECK (("position" >= 0))
);


--
-- Name: graduation_assessment_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_assessment_profiles (
    id character varying(36) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    source_rule_id character varying(160) NOT NULL,
    effective_year integer NOT NULL,
    academic_unit character varying(240) NOT NULL,
    academic_unit_key character varying(240) NOT NULL,
    transition_mode character varying(40) NOT NULL,
    transition_source_text text NOT NULL,
    source_note text,
    requires_manual_review boolean NOT NULL,
    CONSTRAINT ck_graduation_assessment_effective_year CHECK (((effective_year >= 1900) AND (effective_year <= 2100))),
    CONSTRAINT ck_graduation_assessment_transition_mode CHECK (((transition_mode)::text = ANY ((ARRAY['STANDARDIZED_ONLY'::character varying, 'LEGACY_OR_STANDARDIZED'::character varying, 'LEGACY_ONLY'::character varying])::text[])))
);


--
-- Name: graduation_assessment_source_refs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_assessment_source_refs (
    profile_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    source_ref text NOT NULL,
    CONSTRAINT ck_graduation_assessment_source_ref_position CHECK (("position" >= 0))
);


--
-- Name: graduation_credit_profile_academic_unit_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_credit_profile_academic_unit_aliases (
    profile_id character varying(36) CONSTRAINT graduation_credit_profile_academic_unit_ali_profile_id_not_null NOT NULL,
    "position" integer CONSTRAINT graduation_credit_profile_academic_unit_alias_position_not_null NOT NULL,
    alias character varying(240) NOT NULL,
    alias_key character varying(240) CONSTRAINT graduation_credit_profile_academic_unit_alia_alias_key_not_null NOT NULL,
    CONSTRAINT ck_graduation_credit_profile_academic_unit_alias_position CHECK (("position" >= 0))
);


--
-- Name: graduation_credit_profile_source_refs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_credit_profile_source_refs (
    profile_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    source_ref text NOT NULL,
    CONSTRAINT ck_graduation_credit_profile_source_ref_position CHECK (("position" >= 0))
);


--
-- Name: graduation_credit_profile_warnings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_credit_profile_warnings (
    id character varying(36) NOT NULL,
    profile_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    code character varying(80) NOT NULL,
    calculated integer NOT NULL,
    printed integer NOT NULL,
    CONSTRAINT ck_graduation_credit_profile_warning_position CHECK (("position" >= 0)),
    CONSTRAINT ck_graduation_credit_profile_warning_values CHECK (((calculated >= 0) AND (printed >= 0)))
);


--
-- Name: graduation_credit_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_credit_profiles (
    id character varying(36) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    source_rule_id character varying(160) NOT NULL,
    liberal_requirement_set_id character varying(36) NOT NULL,
    academic_unit character varying(240) NOT NULL,
    academic_unit_key character varying(240) NOT NULL,
    admission_year integer NOT NULL,
    student_type character varying(40) NOT NULL,
    program_path character varying(40) NOT NULL,
    total_credits_min integer NOT NULL,
    major_foundation_min integer NOT NULL,
    major_required_min integer NOT NULL,
    major_elective_min integer NOT NULL,
    additional_major_min integer,
    primary_major_min integer NOT NULL,
    secondary_program_min integer,
    requires_manual_review boolean NOT NULL,
    CONSTRAINT ck_graduation_credit_profile_admission_year CHECK (((admission_year >= 1900) AND (admission_year <= 2100))),
    CONSTRAINT ck_graduation_credit_profile_credits CHECK (((total_credits_min >= 0) AND (major_foundation_min >= 0) AND (major_required_min >= 0) AND (major_elective_min >= 0) AND ((additional_major_min IS NULL) OR (additional_major_min >= 0)) AND (primary_major_min >= 0) AND ((secondary_program_min IS NULL) OR (secondary_program_min >= 0)))),
    CONSTRAINT ck_graduation_credit_profile_path CHECK (((program_path)::text = ANY ((ARRAY['ADVANCED_MAJOR'::character varying, 'DOUBLE_MAJOR'::character varying, 'MINOR'::character varying, 'MICRO_MAJOR'::character varying])::text[])))
);


--
-- Name: graduation_legacy_cohorts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_legacy_cohorts (
    id character varying(36) NOT NULL,
    legacy_requirement_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    start_year integer,
    end_year integer,
    expression character varying(160) NOT NULL,
    CONSTRAINT ck_graduation_legacy_cohort_position CHECK (("position" >= 0)),
    CONSTRAINT ck_graduation_legacy_cohort_range CHECK (((start_year IS NULL) OR (end_year IS NULL) OR (start_year <= end_year)))
);


--
-- Name: graduation_legacy_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_legacy_requirements (
    id character varying(36) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    source_rule_id character varying(160) NOT NULL,
    effective_year integer NOT NULL,
    academic_unit character varying(240) NOT NULL,
    academic_unit_key character varying(240) NOT NULL,
    eligibility_requirement text,
    form_thesis boolean NOT NULL,
    form_report boolean NOT NULL,
    form_practical_or_artwork boolean CONSTRAINT graduation_legacy_requiremen_form_practical_or_artwork_not_null NOT NULL,
    form_exam boolean NOT NULL,
    substitute_international_certification text,
    substitute_national_technical_certification text,
    substitute_national_professional_certification text,
    substitute_national_accredited_private_certification text,
    substitute_private_certification text,
    substitute_other text,
    pass_requirement text,
    double_major_pass_requirement text,
    note text,
    requires_manual_review boolean NOT NULL,
    CONSTRAINT ck_graduation_legacy_effective_year CHECK (((effective_year >= 1900) AND (effective_year <= 2100)))
);


--
-- Name: graduation_legacy_source_refs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_legacy_source_refs (
    legacy_requirement_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    source_ref text NOT NULL,
    CONSTRAINT ck_graduation_legacy_source_ref_position CHECK (("position" >= 0))
);


--
-- Name: graduation_liberal_area_requirements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_liberal_area_requirements (
    id character varying(36) NOT NULL,
    requirement_set_id character varying(36) CONSTRAINT graduation_liberal_area_requirement_requirement_set_id_not_null NOT NULL,
    "position" integer NOT NULL,
    area character varying(160) NOT NULL,
    min_courses integer NOT NULL,
    min_credits integer,
    CONSTRAINT ck_graduation_liberal_area_courses CHECK ((min_courses >= 0)),
    CONSTRAINT ck_graduation_liberal_area_credits CHECK (((min_credits IS NULL) OR (min_credits >= 0))),
    CONSTRAINT ck_graduation_liberal_area_position CHECK (("position" >= 0))
);


--
-- Name: graduation_liberal_course_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_liberal_course_aliases (
    id character varying(36) NOT NULL,
    course_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    alias character varying(240) NOT NULL,
    alias_key character varying(240) NOT NULL,
    CONSTRAINT ck_graduation_liberal_course_alias_position CHECK (("position" >= 0))
);


--
-- Name: graduation_liberal_course_terms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_liberal_course_terms (
    course_id character varying(36) NOT NULL,
    semester integer NOT NULL,
    CONSTRAINT ck_graduation_liberal_course_term CHECK ((semester = ANY (ARRAY[1, 2])))
);


--
-- Name: graduation_liberal_required_courses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_liberal_required_courses (
    id character varying(36) NOT NULL,
    requirement_set_id character varying(36) NOT NULL,
    "position" integer NOT NULL,
    course_code character varying(40),
    course_name character varying(240) NOT NULL,
    credits integer NOT NULL,
    grade integer,
    source_page integer NOT NULL,
    CONSTRAINT ck_graduation_liberal_course_credits CHECK ((credits > 0)),
    CONSTRAINT ck_graduation_liberal_course_grade CHECK (((grade IS NULL) OR ((grade >= 1) AND (grade <= 6)))),
    CONSTRAINT ck_graduation_liberal_course_position CHECK (("position" >= 0))
);


--
-- Name: graduation_liberal_requirement_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_liberal_requirement_sets (
    id character varying(36) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    signature character varying(64) NOT NULL,
    admission_year integer NOT NULL,
    student_type character varying(40) NOT NULL,
    required_credits_min integer CONSTRAINT graduation_liberal_requirement_se_required_credits_min_not_null NOT NULL,
    elective_credits_min integer CONSTRAINT graduation_liberal_requirement_se_elective_credits_min_not_null NOT NULL,
    total_credits_min integer NOT NULL,
    total_credits_max integer,
    CONSTRAINT ck_graduation_liberal_set_admission_year CHECK (((admission_year >= 1900) AND (admission_year <= 2100))),
    CONSTRAINT ck_graduation_liberal_set_credits CHECK (((required_credits_min >= 0) AND (elective_credits_min >= 0) AND (total_credits_min >= 0) AND ((total_credits_max IS NULL) OR (total_credits_max >= total_credits_min))))
);


--
-- Name: graduation_requirement_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graduation_requirement_rules (
    id character varying(80) NOT NULL,
    dataset_id character varying(80) NOT NULL,
    rule_kind character varying(80) NOT NULL,
    category_code character varying(40),
    academic_unit character varying(240),
    academic_unit_key character varying(240),
    admission_year_start integer,
    admission_year_end integer,
    effective_year integer,
    student_type character varying(40),
    program_path character varying(40),
    description text,
    requires_manual_review boolean NOT NULL,
    raw_payload json NOT NULL,
    CONSTRAINT ck_graduation_rule_admission_year_range CHECK (((admission_year_start IS NULL) OR (admission_year_end IS NULL) OR (admission_year_start <= admission_year_end)))
);


--
-- Name: historical_archive_manifests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_archive_manifests (
    id character varying(40) NOT NULL,
    schema_version character varying(40) NOT NULL,
    generated_at timestamp with time zone NOT NULL,
    source_checksum character varying(64) NOT NULL,
    raw_payload json NOT NULL,
    source_archive bytea NOT NULL,
    imported_at timestamp with time zone NOT NULL
);


--
-- Name: historical_course_offerings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_course_offerings (
    id character varying(36) NOT NULL,
    dataset_id character varying(20) NOT NULL,
    academic_year integer NOT NULL,
    term_code character varying(8) NOT NULL,
    course_code character varying(40) NOT NULL,
    section_code character varying(40) NOT NULL,
    korean_name character varying(240) NOT NULL,
    english_name character varying(400),
    professor_name character varying(240),
    completion_category character varying(160),
    credits double precision,
    lecture_hours double precision,
    practice_hours double precision,
    raw_lecture_time text,
    raw_location text,
    target_grade character varying(120),
    listing_status character varying(40),
    detail_status character varying(40),
    category_contexts json NOT NULL,
    department_contexts json NOT NULL,
    search_text text NOT NULL,
    department_search_text text NOT NULL,
    raw_payload json NOT NULL
);


--
-- Name: historical_course_relations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_course_relations (
    id character varying(36) NOT NULL,
    dataset_id character varying(40) NOT NULL,
    relation_type character varying(24) NOT NULL,
    designated_year character varying(20),
    designated_term character varying(20),
    original_course_name character varying(240) NOT NULL,
    original_category character varying(160),
    original_credits double precision,
    original_college character varying(240),
    original_department character varying(240),
    related_course_name character varying(240) NOT NULL,
    related_category character varying(160),
    related_credits double precision,
    related_department character varying(240),
    note text,
    raw_payload json NOT NULL
);


--
-- Name: historical_curriculum_datasets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_curriculum_datasets (
    id character varying(20) NOT NULL,
    academic_year integer NOT NULL,
    schema_version character varying(40) NOT NULL,
    collected_at timestamp with time zone NOT NULL,
    source_checksum character varying(64) NOT NULL,
    department_count integer NOT NULL,
    course_record_count integer NOT NULL,
    raw_payload json NOT NULL,
    source_archive bytea NOT NULL,
    imported_at timestamp with time zone NOT NULL
);


--
-- Name: historical_curriculum_departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_curriculum_departments (
    id character varying(36) NOT NULL,
    dataset_id character varying(20) NOT NULL,
    academic_year integer NOT NULL,
    college_code character varying(40),
    college_name character varying(240),
    department_code character varying(40) NOT NULL,
    department_name character varying(240) NOT NULL,
    course_count integer NOT NULL,
    raw_payload json NOT NULL
);


--
-- Name: historical_relation_datasets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_relation_datasets (
    id character varying(40) NOT NULL,
    schema_version character varying(40) NOT NULL,
    collected_at timestamp with time zone NOT NULL,
    source_checksum character varying(64) NOT NULL,
    replacement_count integer NOT NULL,
    equivalent_count integer NOT NULL,
    raw_payload json NOT NULL,
    source_archive bytea NOT NULL,
    imported_at timestamp with time zone NOT NULL
);


--
-- Name: historical_term_datasets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historical_term_datasets (
    id character varying(20) NOT NULL,
    academic_year integer NOT NULL,
    term_code character varying(8) NOT NULL,
    term_name character varying(80) NOT NULL,
    data_status character varying(24) NOT NULL,
    schema_version character varying(40) NOT NULL,
    collected_at timestamp with time zone NOT NULL,
    source_checksum character varying(64) NOT NULL,
    record_count integer NOT NULL,
    raw_payload json NOT NULL,
    source_archive bytea NOT NULL,
    imported_at timestamp with time zone NOT NULL
);


--
-- Name: requirement_datasets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requirement_datasets (
    id character varying(80) NOT NULL,
    kind character varying(40) NOT NULL,
    schema_version character varying(40) NOT NULL,
    admission_year integer,
    effective_year integer,
    as_of date,
    source_path text NOT NULL,
    source_checksum character varying(64) NOT NULL,
    normalized_checksum character varying(64) NOT NULL,
    record_count integer NOT NULL,
    raw_payload json NOT NULL,
    imported_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_requirement_datasets_admission_year CHECK (((admission_year IS NULL) OR ((admission_year >= 1900) AND (admission_year <= 2100)))),
    CONSTRAINT ck_requirement_datasets_effective_year CHECK (((effective_year IS NULL) OR ((effective_year >= 1900) AND (effective_year <= 2100)))),
    CONSTRAINT ck_requirement_datasets_record_count CHECK ((record_count >= 0))
);


--
-- Name: rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rooms (
    semester_id character varying(20) NOT NULL,
    code character varying(40) NOT NULL,
    building_code character varying(40),
    building_name text,
    label text,
    room_type text,
    capacity integer
);


--
-- Name: sections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sections (
    semester_id character varying(20) NOT NULL,
    course_code character varying(40) NOT NULL,
    section_code character varying(20) NOT NULL,
    professor text,
    raw_lecture_time text NOT NULL,
    time_to_be_announced boolean NOT NULL,
    warning_codes jsonb NOT NULL
);


--
-- Name: semesters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.semesters (
    id character varying(20) NOT NULL,
    prepared_at date NOT NULL,
    dataset_version character varying(64) NOT NULL,
    source_checksum character varying(64) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sessions (
    id bigint NOT NULL,
    semester_id character varying(20) NOT NULL,
    course_code character varying(40) NOT NULL,
    section_code character varying(20) NOT NULL,
    day character varying(1) NOT NULL,
    start_minute smallint NOT NULL,
    end_minute smallint NOT NULL,
    room_code character varying(40),
    CONSTRAINT ck_session_day CHECK (((day)::text = ANY ((ARRAY['월'::character varying, '화'::character varying, '수'::character varying, '목'::character varying, '금'::character varying, '토'::character varying, '일'::character varying])::text[]))),
    CONSTRAINT ck_session_end CHECK (((end_minute > start_minute) AND (end_minute <= 1440))),
    CONSTRAINT ck_session_start CHECK (((start_minute >= 0) AND (start_minute < 1440)))
);


--
-- Name: sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.sessions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.sessions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: courses courses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.courses
    ADD CONSTRAINT courses_pkey PRIMARY KEY (semester_id, course_code);


--
-- Name: curriculum_program_aliases curriculum_program_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_aliases
    ADD CONSTRAINT curriculum_program_aliases_pkey PRIMARY KEY (id);


--
-- Name: curriculum_program_requirements curriculum_program_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_requirements
    ADD CONSTRAINT curriculum_program_requirements_pkey PRIMARY KEY (id);


--
-- Name: curriculum_required_courses curriculum_required_courses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_required_courses
    ADD CONSTRAINT curriculum_required_courses_pkey PRIMARY KEY (id);


--
-- Name: data_imports data_imports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_imports
    ADD CONSTRAINT data_imports_pkey PRIMARY KEY (id);


--
-- Name: data_imports data_imports_semester_id_checksum_parser_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_imports
    ADD CONSTRAINT data_imports_semester_id_checksum_parser_version_key UNIQUE (semester_id, checksum, parser_version);


--
-- Name: graduation_assessment_categories graduation_assessment_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_categories
    ADD CONSTRAINT graduation_assessment_categories_pkey PRIMARY KEY (id);


--
-- Name: graduation_assessment_credentials graduation_assessment_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_credentials
    ADD CONSTRAINT graduation_assessment_credentials_pkey PRIMARY KEY (id);


--
-- Name: graduation_assessment_profiles graduation_assessment_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_profiles
    ADD CONSTRAINT graduation_assessment_profiles_pkey PRIMARY KEY (id);


--
-- Name: graduation_assessment_source_refs graduation_assessment_source_refs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_source_refs
    ADD CONSTRAINT graduation_assessment_source_refs_pkey PRIMARY KEY (profile_id, "position");


--
-- Name: graduation_credit_profile_academic_unit_aliases graduation_credit_profile_academic_unit_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_academic_unit_aliases
    ADD CONSTRAINT graduation_credit_profile_academic_unit_aliases_pkey PRIMARY KEY (profile_id, "position");


--
-- Name: graduation_credit_profile_source_refs graduation_credit_profile_source_refs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_source_refs
    ADD CONSTRAINT graduation_credit_profile_source_refs_pkey PRIMARY KEY (profile_id, "position");


--
-- Name: graduation_credit_profile_warnings graduation_credit_profile_warnings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_warnings
    ADD CONSTRAINT graduation_credit_profile_warnings_pkey PRIMARY KEY (id);


--
-- Name: graduation_credit_profiles graduation_credit_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profiles
    ADD CONSTRAINT graduation_credit_profiles_pkey PRIMARY KEY (id);


--
-- Name: graduation_legacy_cohorts graduation_legacy_cohorts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_cohorts
    ADD CONSTRAINT graduation_legacy_cohorts_pkey PRIMARY KEY (id);


--
-- Name: graduation_legacy_requirements graduation_legacy_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_requirements
    ADD CONSTRAINT graduation_legacy_requirements_pkey PRIMARY KEY (id);


--
-- Name: graduation_legacy_source_refs graduation_legacy_source_refs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_source_refs
    ADD CONSTRAINT graduation_legacy_source_refs_pkey PRIMARY KEY (legacy_requirement_id, "position");


--
-- Name: graduation_liberal_area_requirements graduation_liberal_area_requirements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_area_requirements
    ADD CONSTRAINT graduation_liberal_area_requirements_pkey PRIMARY KEY (id);


--
-- Name: graduation_liberal_course_aliases graduation_liberal_course_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_aliases
    ADD CONSTRAINT graduation_liberal_course_aliases_pkey PRIMARY KEY (id);


--
-- Name: graduation_liberal_course_terms graduation_liberal_course_terms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_terms
    ADD CONSTRAINT graduation_liberal_course_terms_pkey PRIMARY KEY (course_id, semester);


--
-- Name: graduation_liberal_required_courses graduation_liberal_required_courses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_required_courses
    ADD CONSTRAINT graduation_liberal_required_courses_pkey PRIMARY KEY (id);


--
-- Name: graduation_liberal_requirement_sets graduation_liberal_requirement_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_requirement_sets
    ADD CONSTRAINT graduation_liberal_requirement_sets_pkey PRIMARY KEY (id);


--
-- Name: graduation_requirement_rules graduation_requirement_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_requirement_rules
    ADD CONSTRAINT graduation_requirement_rules_pkey PRIMARY KEY (id);


--
-- Name: historical_archive_manifests historical_archive_manifests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_archive_manifests
    ADD CONSTRAINT historical_archive_manifests_pkey PRIMARY KEY (id);


--
-- Name: historical_course_offerings historical_course_offerings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_course_offerings
    ADD CONSTRAINT historical_course_offerings_pkey PRIMARY KEY (id);


--
-- Name: historical_course_relations historical_course_relations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_course_relations
    ADD CONSTRAINT historical_course_relations_pkey PRIMARY KEY (id);


--
-- Name: historical_curriculum_datasets historical_curriculum_datasets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_curriculum_datasets
    ADD CONSTRAINT historical_curriculum_datasets_pkey PRIMARY KEY (id);


--
-- Name: historical_curriculum_departments historical_curriculum_departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_curriculum_departments
    ADD CONSTRAINT historical_curriculum_departments_pkey PRIMARY KEY (id);


--
-- Name: historical_relation_datasets historical_relation_datasets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_relation_datasets
    ADD CONSTRAINT historical_relation_datasets_pkey PRIMARY KEY (id);


--
-- Name: historical_term_datasets historical_term_datasets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_term_datasets
    ADD CONSTRAINT historical_term_datasets_pkey PRIMARY KEY (id);


--
-- Name: requirement_datasets requirement_datasets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requirement_datasets
    ADD CONSTRAINT requirement_datasets_pkey PRIMARY KEY (id);


--
-- Name: rooms rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT rooms_pkey PRIMARY KEY (semester_id, code);


--
-- Name: sections sections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sections
    ADD CONSTRAINT sections_pkey PRIMARY KEY (semester_id, course_code, section_code);


--
-- Name: semesters semesters_dataset_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semesters
    ADD CONSTRAINT semesters_dataset_version_key UNIQUE (dataset_version);


--
-- Name: semesters semesters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semesters
    ADD CONSTRAINT semesters_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: curriculum_program_aliases uq_curriculum_program_alias; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_aliases
    ADD CONSTRAINT uq_curriculum_program_alias UNIQUE (admission_year, alias_key, program_id);


--
-- Name: curriculum_program_requirements uq_curriculum_program_requirement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_requirements
    ADD CONSTRAINT uq_curriculum_program_requirement UNIQUE (dataset_id, academic_unit_key);


--
-- Name: curriculum_required_courses uq_curriculum_required_course; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_required_courses
    ADD CONSTRAINT uq_curriculum_required_course UNIQUE (program_id, classification, course_code);


--
-- Name: graduation_assessment_categories uq_graduation_assessment_category; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_categories
    ADD CONSTRAINT uq_graduation_assessment_category UNIQUE (profile_id, category_code);


--
-- Name: graduation_assessment_credentials uq_graduation_assessment_credential_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_credentials
    ADD CONSTRAINT uq_graduation_assessment_credential_position UNIQUE (profile_id, "position");


--
-- Name: graduation_assessment_profiles uq_graduation_assessment_profile_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_profiles
    ADD CONSTRAINT uq_graduation_assessment_profile_scope UNIQUE (dataset_id, academic_unit_key, effective_year);


--
-- Name: graduation_assessment_profiles uq_graduation_assessment_profile_source_rule; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_profiles
    ADD CONSTRAINT uq_graduation_assessment_profile_source_rule UNIQUE (dataset_id, source_rule_id);


--
-- Name: graduation_assessment_source_refs uq_graduation_assessment_source_ref; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_source_refs
    ADD CONSTRAINT uq_graduation_assessment_source_ref UNIQUE (profile_id, source_ref);


--
-- Name: graduation_credit_profile_academic_unit_aliases uq_graduation_credit_profile_academic_unit_alias; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_academic_unit_aliases
    ADD CONSTRAINT uq_graduation_credit_profile_academic_unit_alias UNIQUE (profile_id, alias_key);


--
-- Name: graduation_credit_profiles uq_graduation_credit_profile_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profiles
    ADD CONSTRAINT uq_graduation_credit_profile_scope UNIQUE (dataset_id, academic_unit_key, admission_year, student_type, program_path);


--
-- Name: graduation_credit_profile_source_refs uq_graduation_credit_profile_source_ref; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_source_refs
    ADD CONSTRAINT uq_graduation_credit_profile_source_ref UNIQUE (profile_id, source_ref);


--
-- Name: graduation_credit_profiles uq_graduation_credit_profile_source_rule; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profiles
    ADD CONSTRAINT uq_graduation_credit_profile_source_rule UNIQUE (dataset_id, source_rule_id);


--
-- Name: graduation_credit_profile_warnings uq_graduation_credit_profile_warning; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_warnings
    ADD CONSTRAINT uq_graduation_credit_profile_warning UNIQUE (profile_id, code);


--
-- Name: graduation_credit_profile_warnings uq_graduation_credit_profile_warning_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_warnings
    ADD CONSTRAINT uq_graduation_credit_profile_warning_position UNIQUE (profile_id, "position");


--
-- Name: graduation_legacy_cohorts uq_graduation_legacy_cohort_expression; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_cohorts
    ADD CONSTRAINT uq_graduation_legacy_cohort_expression UNIQUE (legacy_requirement_id, expression);


--
-- Name: graduation_legacy_cohorts uq_graduation_legacy_cohort_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_cohorts
    ADD CONSTRAINT uq_graduation_legacy_cohort_position UNIQUE (legacy_requirement_id, "position");


--
-- Name: graduation_legacy_requirements uq_graduation_legacy_requirement_source_rule; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_requirements
    ADD CONSTRAINT uq_graduation_legacy_requirement_source_rule UNIQUE (dataset_id, source_rule_id);


--
-- Name: graduation_legacy_source_refs uq_graduation_legacy_source_ref; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_source_refs
    ADD CONSTRAINT uq_graduation_legacy_source_ref UNIQUE (legacy_requirement_id, source_ref);


--
-- Name: graduation_liberal_area_requirements uq_graduation_liberal_area; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_area_requirements
    ADD CONSTRAINT uq_graduation_liberal_area UNIQUE (requirement_set_id, area);


--
-- Name: graduation_liberal_area_requirements uq_graduation_liberal_area_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_area_requirements
    ADD CONSTRAINT uq_graduation_liberal_area_position UNIQUE (requirement_set_id, "position");


--
-- Name: graduation_liberal_course_aliases uq_graduation_liberal_course_alias; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_aliases
    ADD CONSTRAINT uq_graduation_liberal_course_alias UNIQUE (course_id, alias_key);


--
-- Name: graduation_liberal_course_aliases uq_graduation_liberal_course_alias_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_aliases
    ADD CONSTRAINT uq_graduation_liberal_course_alias_position UNIQUE (course_id, "position");


--
-- Name: graduation_liberal_required_courses uq_graduation_liberal_course_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_required_courses
    ADD CONSTRAINT uq_graduation_liberal_course_code UNIQUE (requirement_set_id, course_code);


--
-- Name: graduation_liberal_required_courses uq_graduation_liberal_course_position; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_required_courses
    ADD CONSTRAINT uq_graduation_liberal_course_position UNIQUE (requirement_set_id, "position");


--
-- Name: graduation_liberal_requirement_sets uq_graduation_liberal_requirement_set_signature; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_requirement_sets
    ADD CONSTRAINT uq_graduation_liberal_requirement_set_signature UNIQUE (dataset_id, signature);


--
-- Name: historical_curriculum_departments uq_historical_curriculum_department; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_curriculum_departments
    ADD CONSTRAINT uq_historical_curriculum_department UNIQUE (academic_year, department_code);


--
-- Name: historical_course_offerings uq_historical_offering_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_course_offerings
    ADD CONSTRAINT uq_historical_offering_identity UNIQUE (academic_year, term_code, course_code, section_code);


--
-- Name: historical_term_datasets uq_historical_term_year_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_term_datasets
    ADD CONSTRAINT uq_historical_term_year_code UNIQUE (academic_year, term_code);


--
-- Name: ix_curriculum_program_aliases_program_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_program_aliases_program_id ON public.curriculum_program_aliases USING btree (program_id);


--
-- Name: ix_curriculum_program_aliases_year_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_program_aliases_year_key ON public.curriculum_program_aliases USING btree (admission_year, alias_key);


--
-- Name: ix_curriculum_program_requirements_year_unit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_program_requirements_year_unit ON public.curriculum_program_requirements USING btree (admission_year, academic_unit_key);


--
-- Name: ix_curriculum_required_courses_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_required_courses_code ON public.curriculum_required_courses USING btree (course_code);


--
-- Name: ix_curriculum_required_courses_program; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_required_courses_program ON public.curriculum_required_courses USING btree (program_id, classification);


--
-- Name: ix_graduation_assessment_profile_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_assessment_profile_lookup ON public.graduation_assessment_profiles USING btree (academic_unit_key, effective_year);


--
-- Name: ix_graduation_credit_profile_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_credit_profile_lookup ON public.graduation_credit_profiles USING btree (academic_unit_key, admission_year, student_type, program_path);


--
-- Name: ix_graduation_credit_profiles_liberal_requirement_set_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_credit_profiles_liberal_requirement_set_id ON public.graduation_credit_profiles USING btree (liberal_requirement_set_id);


--
-- Name: ix_graduation_legacy_requirement_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_legacy_requirement_lookup ON public.graduation_legacy_requirements USING btree (academic_unit_key, effective_year);


--
-- Name: ix_graduation_liberal_sets_year_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_liberal_sets_year_type ON public.graduation_liberal_requirement_sets USING btree (admission_year, student_type);


--
-- Name: ix_graduation_requirement_rules_dataset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_requirement_rules_dataset ON public.graduation_requirement_rules USING btree (dataset_id, rule_kind);


--
-- Name: ix_graduation_requirement_rules_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_graduation_requirement_rules_lookup ON public.graduation_requirement_rules USING btree (academic_unit_key, admission_year_start, admission_year_end, effective_year);


--
-- Name: ix_historical_course_offerings_dataset_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_course_offerings_dataset_id ON public.historical_course_offerings USING btree (dataset_id);


--
-- Name: ix_historical_course_relations_dataset_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_course_relations_dataset_id ON public.historical_course_relations USING btree (dataset_id);


--
-- Name: ix_historical_curriculum_datasets_academic_year; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ix_historical_curriculum_datasets_academic_year ON public.historical_curriculum_datasets USING btree (academic_year);


--
-- Name: ix_historical_curriculum_department_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_curriculum_department_name ON public.historical_curriculum_departments USING btree (department_name);


--
-- Name: ix_historical_curriculum_departments_dataset_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_curriculum_departments_dataset_id ON public.historical_curriculum_departments USING btree (dataset_id);


--
-- Name: ix_historical_offering_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_offering_category ON public.historical_course_offerings USING btree (completion_category);


--
-- Name: ix_historical_offering_course; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_offering_course ON public.historical_course_offerings USING btree (course_code);


--
-- Name: ix_historical_offering_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_offering_name ON public.historical_course_offerings USING btree (korean_name);


--
-- Name: ix_historical_offering_term; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_offering_term ON public.historical_course_offerings USING btree (academic_year, term_code);


--
-- Name: ix_historical_relation_original_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_relation_original_name ON public.historical_course_relations USING btree (original_course_name);


--
-- Name: ix_historical_relation_related_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_relation_related_name ON public.historical_course_relations USING btree (related_course_name);


--
-- Name: ix_historical_relation_type_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_relation_type_year ON public.historical_course_relations USING btree (relation_type, designated_year);


--
-- Name: ix_historical_term_year_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historical_term_year_code ON public.historical_term_datasets USING btree (academic_year, term_code);


--
-- Name: ix_requirement_datasets_kind_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_requirement_datasets_kind_year ON public.requirement_datasets USING btree (kind, admission_year, effective_year);


--
-- Name: ix_sessions_section; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sessions_section ON public.sessions USING btree (semester_id, course_code, section_code);


--
-- Name: courses courses_semester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.courses
    ADD CONSTRAINT courses_semester_id_fkey FOREIGN KEY (semester_id) REFERENCES public.semesters(id) ON DELETE CASCADE;


--
-- Name: curriculum_program_aliases curriculum_program_aliases_program_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_aliases
    ADD CONSTRAINT curriculum_program_aliases_program_id_fkey FOREIGN KEY (program_id) REFERENCES public.curriculum_program_requirements(id) ON DELETE CASCADE;


--
-- Name: curriculum_program_requirements curriculum_program_requirements_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_program_requirements
    ADD CONSTRAINT curriculum_program_requirements_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: curriculum_required_courses curriculum_required_courses_program_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_required_courses
    ADD CONSTRAINT curriculum_required_courses_program_id_fkey FOREIGN KEY (program_id) REFERENCES public.curriculum_program_requirements(id) ON DELETE CASCADE;


--
-- Name: data_imports data_imports_semester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_imports
    ADD CONSTRAINT data_imports_semester_id_fkey FOREIGN KEY (semester_id) REFERENCES public.semesters(id) ON DELETE CASCADE;


--
-- Name: graduation_assessment_categories graduation_assessment_categories_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_categories
    ADD CONSTRAINT graduation_assessment_categories_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_assessment_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_assessment_credentials graduation_assessment_credentials_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_credentials
    ADD CONSTRAINT graduation_assessment_credentials_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_assessment_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_assessment_profiles graduation_assessment_profiles_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_profiles
    ADD CONSTRAINT graduation_assessment_profiles_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: graduation_assessment_source_refs graduation_assessment_source_refs_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_assessment_source_refs
    ADD CONSTRAINT graduation_assessment_source_refs_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_assessment_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_credit_profile_academic_unit_aliases graduation_credit_profile_academic_unit_aliases_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_academic_unit_aliases
    ADD CONSTRAINT graduation_credit_profile_academic_unit_aliases_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_credit_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_credit_profile_source_refs graduation_credit_profile_source_refs_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_source_refs
    ADD CONSTRAINT graduation_credit_profile_source_refs_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_credit_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_credit_profile_warnings graduation_credit_profile_warnings_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profile_warnings
    ADD CONSTRAINT graduation_credit_profile_warnings_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.graduation_credit_profiles(id) ON DELETE CASCADE;


--
-- Name: graduation_credit_profiles graduation_credit_profiles_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profiles
    ADD CONSTRAINT graduation_credit_profiles_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: graduation_credit_profiles graduation_credit_profiles_liberal_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_credit_profiles
    ADD CONSTRAINT graduation_credit_profiles_liberal_requirement_set_id_fkey FOREIGN KEY (liberal_requirement_set_id) REFERENCES public.graduation_liberal_requirement_sets(id) ON DELETE RESTRICT;


--
-- Name: graduation_legacy_cohorts graduation_legacy_cohorts_legacy_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_cohorts
    ADD CONSTRAINT graduation_legacy_cohorts_legacy_requirement_id_fkey FOREIGN KEY (legacy_requirement_id) REFERENCES public.graduation_legacy_requirements(id) ON DELETE CASCADE;


--
-- Name: graduation_legacy_requirements graduation_legacy_requirements_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_requirements
    ADD CONSTRAINT graduation_legacy_requirements_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: graduation_legacy_source_refs graduation_legacy_source_refs_legacy_requirement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_legacy_source_refs
    ADD CONSTRAINT graduation_legacy_source_refs_legacy_requirement_id_fkey FOREIGN KEY (legacy_requirement_id) REFERENCES public.graduation_legacy_requirements(id) ON DELETE CASCADE;


--
-- Name: graduation_liberal_area_requirements graduation_liberal_area_requirements_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_area_requirements
    ADD CONSTRAINT graduation_liberal_area_requirements_requirement_set_id_fkey FOREIGN KEY (requirement_set_id) REFERENCES public.graduation_liberal_requirement_sets(id) ON DELETE CASCADE;


--
-- Name: graduation_liberal_course_aliases graduation_liberal_course_aliases_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_aliases
    ADD CONSTRAINT graduation_liberal_course_aliases_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.graduation_liberal_required_courses(id) ON DELETE CASCADE;


--
-- Name: graduation_liberal_course_terms graduation_liberal_course_terms_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_course_terms
    ADD CONSTRAINT graduation_liberal_course_terms_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.graduation_liberal_required_courses(id) ON DELETE CASCADE;


--
-- Name: graduation_liberal_required_courses graduation_liberal_required_courses_requirement_set_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_required_courses
    ADD CONSTRAINT graduation_liberal_required_courses_requirement_set_id_fkey FOREIGN KEY (requirement_set_id) REFERENCES public.graduation_liberal_requirement_sets(id) ON DELETE CASCADE;


--
-- Name: graduation_liberal_requirement_sets graduation_liberal_requirement_sets_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_liberal_requirement_sets
    ADD CONSTRAINT graduation_liberal_requirement_sets_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: graduation_requirement_rules graduation_requirement_rules_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graduation_requirement_rules
    ADD CONSTRAINT graduation_requirement_rules_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.requirement_datasets(id) ON DELETE CASCADE;


--
-- Name: historical_course_offerings historical_course_offerings_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_course_offerings
    ADD CONSTRAINT historical_course_offerings_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.historical_term_datasets(id) ON DELETE CASCADE;


--
-- Name: historical_course_relations historical_course_relations_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_course_relations
    ADD CONSTRAINT historical_course_relations_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.historical_relation_datasets(id) ON DELETE CASCADE;


--
-- Name: historical_curriculum_departments historical_curriculum_departments_dataset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historical_curriculum_departments
    ADD CONSTRAINT historical_curriculum_departments_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES public.historical_curriculum_datasets(id) ON DELETE CASCADE;


--
-- Name: rooms rooms_semester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rooms
    ADD CONSTRAINT rooms_semester_id_fkey FOREIGN KEY (semester_id) REFERENCES public.semesters(id) ON DELETE CASCADE;


--
-- Name: sections sections_semester_id_course_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sections
    ADD CONSTRAINT sections_semester_id_course_code_fkey FOREIGN KEY (semester_id, course_code) REFERENCES public.courses(semester_id, course_code) ON DELETE CASCADE;


--
-- Name: sessions sessions_semester_id_course_code_section_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_semester_id_course_code_section_code_fkey FOREIGN KEY (semester_id, course_code, section_code) REFERENCES public.sections(semester_id, course_code, section_code) ON DELETE CASCADE;


--
-- Name: sessions sessions_semester_id_room_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_semester_id_room_code_fkey FOREIGN KEY (semester_id, room_code) REFERENCES public.rooms(semester_id, code);


--
-- PostgreSQL database dump complete
--
