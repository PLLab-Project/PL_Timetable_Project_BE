CREATE TABLE reference_data_imports (
    package_id varchar(100) PRIMARY KEY,
    source_database_version varchar(40) NOT NULL,
    report jsonb NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE reference_data_imports IS
    'Successful immutable reference-data packages; used to make repeated bootstrap ingestion non-destructive.';
