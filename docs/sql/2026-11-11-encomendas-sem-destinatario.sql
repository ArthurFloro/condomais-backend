-- Encomenda sem destinatário identificado (CONDO-05.1) e dados de quem retirou (CONDO-05).
-- Rode ANTES do merge: a versão atual continua funcionando (só passa a aceitar unidade nula e colunas novas
-- vazias), e a nova não sobe sem as colunas (spring.jpa.hibernate.ddl-auto=validate). Pode rodar mais de uma vez.

-- 1) A unidade passa a ser opcional: nula = chegou sem destinatário identificado
ALTER TABLE encomendas ALTER COLUMN apartamento_id DROP NOT NULL;

-- 2) Quem retirou: nome (em toda retirada) e, na encomenda sem unidade, o apartamento que a pessoa informou
ALTER TABLE encomendas ADD COLUMN IF NOT EXISTS retirante_nome varchar(255);
ALTER TABLE encomendas ADD COLUMN IF NOT EXISTS retirante_apartamento_id uuid;

ALTER TABLE encomendas DROP CONSTRAINT IF EXISTS fk_encomendas_retirante_apartamento;
ALTER TABLE encomendas ADD CONSTRAINT fk_encomendas_retirante_apartamento
    FOREIGN KEY (retirante_apartamento_id) REFERENCES apartamentos (id);
