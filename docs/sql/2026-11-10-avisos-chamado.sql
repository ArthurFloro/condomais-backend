-- Comunicado ligado a um chamado (ex.: "Dado o chamado @3F2A1C9E, a manutenção será amanhã").
-- Rode ANTES do merge: a coluna é opcional, então a versão atual continua funcionando com ela, e a nova não
-- sobe sem ela (spring.jpa.hibernate.ddl-auto=validate). Pode rodar mais de uma vez.
ALTER TABLE avisos ADD COLUMN IF NOT EXISTS chamado_id uuid;

ALTER TABLE avisos DROP CONSTRAINT IF EXISTS fk_avisos_chamado;
ALTER TABLE avisos ADD CONSTRAINT fk_avisos_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id);
