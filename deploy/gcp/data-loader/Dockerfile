# syntax=docker/dockerfile:1.7

FROM postgres:18.4-alpine

RUN apk add --no-cache ca-certificates curl jq

WORKDIR /workspace

COPY --chmod=0555 scripts/ingest-academic-data.sh scripts/verify-database.sh /workspace/scripts/
COPY data/database/SHA256SUMS data/database/expected-row-counts.tsv /workspace/static/
COPY backend/src/main/resources/db/normalization/normalize_academic_units.sql /workspace/normalization/
COPY --chmod=0555 deploy/gcp/data-loader/entrypoint.sh /workspace/entrypoint.sh

USER postgres

ENTRYPOINT ["/workspace/entrypoint.sh"]
