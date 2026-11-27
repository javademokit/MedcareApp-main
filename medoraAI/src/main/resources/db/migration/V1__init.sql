CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
  id UUID PRIMARY KEY, email TEXT UNIQUE NOT NULL, name TEXT, password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('USER','ADMIN')), enabled BOOLEAN DEFAULT TRUE, created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE conversations (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT, created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE messages (
  id UUID PRIMARY KEY, conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
  content TEXT, status TEXT DEFAULT 'COMPLETE',
  sources JSONB, model TEXT, prompt_version TEXT,
  prompt_tokens INT, completion_tokens INT, latency_ms INT, created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON messages (conversation_id, created_at);

CREATE TABLE documents (
  id UUID PRIMARY KEY, name TEXT NOT NULL, content_type TEXT, size_bytes BIGINT, sha256 TEXT NOT NULL,
  category TEXT, version INT DEFAULT 1, status TEXT NOT NULL,
  error_message TEXT, chunk_count INT DEFAULT 0, storage_path TEXT,
  uploaded_by UUID REFERENCES users(id), uploaded_at TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX ON documents (sha256);

CREATE TABLE feedback (
  id UUID PRIMARY KEY, message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id), rating TEXT NOT NULL CHECK (rating IN ('UP','DOWN')),
  comment TEXT, created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY, user_id UUID, action TEXT NOT NULL, ip TEXT, details JSONB, created_at TIMESTAMPTZ DEFAULT now()
);
