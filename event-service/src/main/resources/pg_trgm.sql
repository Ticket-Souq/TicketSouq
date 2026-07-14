CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_events_title_trgm ON events USING GIN (title gin_trgm_ops);
CREATE INDEX idx_sections_name_trgm ON sections USING GIN (name gin_trgm_ops);
-- TODO fix
