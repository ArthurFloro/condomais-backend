-- Visitas com autorização prévia (período de entrada, PENDENTE/AUTORIZADO/RECUSADO) e visitantes por condomínio.
-- Rode no banco LOGO ANTES do merge: com spring.jpa.hibernate.ddl-auto=validate, a versão nova não sobe sem
-- as colunas. Pode rodar mais de uma vez.
--
-- Até aqui o cadastro de visitantes era global (CPF único no sistema todo): a portaria de um condomínio
-- reaproveitava o nome e o telefone que outro condomínio tinha registrado. Agora cada condomínio tem o seu.

BEGIN;

-- 1. Visitas: fim do período autorizado, quando foi cadastrada e por quem (PORTARIA ou MORADOR)
ALTER TABLE visitas ADD COLUMN IF NOT EXISTS data_fim_prevista date;
ALTER TABLE visitas ADD COLUMN IF NOT EXISTS horario_fim_previsto time(0);
ALTER TABLE visitas ADD COLUMN IF NOT EXISTS data_hora_registro timestamp(6);
ALTER TABLE visitas ADD COLUMN IF NOT EXISTS origem varchar(20);
UPDATE visitas SET data_hora_registro = coalesce(data_hora_entrada, now()) WHERE data_hora_registro IS NULL;
UPDATE visitas SET origem = 'PORTARIA' WHERE origem IS NULL;

-- 2. Visitantes passam a pertencer a um condomínio
ALTER TABLE visitantes ADD COLUMN IF NOT EXISTS condominio_id uuid;

-- O CPF deixa de ser único no sistema todo (o nome da constraint varia conforme quem criou a tabela)
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid = 'visitantes'::regclass AND contype = 'u' LOOP
        EXECUTE format('ALTER TABLE visitantes DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

-- Cada visitante antigo vira um cadastro por condomínio em que tem visita...
INSERT INTO visitantes (id, cpf, nome, telefone, condominio_id)
SELECT gen_random_uuid(), v.cpf, v.nome, v.telefone, x.condominio_id
FROM visitantes v
JOIN (SELECT DISTINCT visitante_id, condominio_id FROM visitas) x ON x.visitante_id = v.id
WHERE v.condominio_id IS NULL;

-- ...as visitas passam a apontar para o cadastro do próprio condomínio...
UPDATE visitas vi SET visitante_id = novo.id
FROM visitantes antigo, visitantes novo
WHERE vi.visitante_id = antigo.id AND antigo.condominio_id IS NULL
  AND novo.cpf = antigo.cpf AND novo.condominio_id = vi.condominio_id;

-- ...e o cadastro global, que ficou sem visitas, sai
DELETE FROM visitantes WHERE condominio_id IS NULL;

ALTER TABLE visitantes ALTER COLUMN condominio_id SET NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_visitantes_condominio') THEN
        ALTER TABLE visitantes ADD CONSTRAINT fk_visitantes_condominio FOREIGN KEY (condominio_id) REFERENCES condominios;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS visitantes_cpf_unico_no_condominio ON visitantes (condominio_id, cpf);

COMMIT;

-- Para conferir depois:
-- SELECT count(*) FROM visitantes WHERE condominio_id IS NULL;   -- 0
-- SELECT count(*) FROM visitas vi JOIN visitantes v ON v.id = vi.visitante_id WHERE v.condominio_id <> vi.condominio_id;  -- 0
