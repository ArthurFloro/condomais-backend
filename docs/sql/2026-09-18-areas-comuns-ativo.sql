-- Soft-delete de áreas comuns (PUT /condominios/areas-comuns/{id}/desativar).
-- O projeto usa spring.jpa.hibernate.ddl-auto=validate, então a coluna precisa
-- existir no banco antes de subir a aplicação.
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;
