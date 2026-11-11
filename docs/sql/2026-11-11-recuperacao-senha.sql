-- Recuperação de senha por e-mail: um pedido por link enviado. Guarda só o hash SHA-256 do token do link.
-- Rode ANTES do merge: a tabela nova não afeta a versão atual, e a nova não sobe sem ela
-- (spring.jpa.hibernate.ddl-auto=validate). Pode rodar mais de uma vez.
CREATE TABLE IF NOT EXISTS recuperacoes_senha (
    id uuid NOT NULL PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    criado_em timestamp(6) NOT NULL,
    expira_em timestamp(6) NOT NULL,
    usado_em timestamp(6)
);
-- O limite de pedidos por hora conta os pedidos recentes do usuário
CREATE INDEX IF NOT EXISTS recuperacoes_senha_usuario ON recuperacoes_senha (usuario_id, criado_em);
