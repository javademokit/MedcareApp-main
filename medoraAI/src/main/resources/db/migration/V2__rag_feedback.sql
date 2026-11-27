CREATE TABLE rag_feedback (
  id BIGSERIAL PRIMARY KEY,
  question TEXT NOT NULL,
  rating TEXT NOT NULL CHECK (rating IN ('positive', 'negative')),
  comment TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON rag_feedback (rating);
CREATE INDEX ON rag_feedback (created_at);
