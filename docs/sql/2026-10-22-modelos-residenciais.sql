-- Modelos residenciais do Setup Wizard (VERTICAL, HORIZONTAL, EDIFICIO), andar da unidade e capacidade da área comum.
-- Rode no banco ANTES do merge: com spring.jpa.hibernate.ddl-auto=validate, a versão nova não sobe sem as
-- colunas. É compatível com a versão anterior (todas têm default ou aceitam nulo).

-- Condomínios que já existem são verticais (torres com apartamentos)
ALTER TABLE condominios ADD COLUMN IF NOT EXISTS modelo varchar(20) NOT NULL DEFAULT 'VERTICAL';

-- Andar da unidade; nulo para casas (HORIZONTAL) e para unidades antigas
ALTER TABLE apartamentos ADD COLUMN IF NOT EXISTS andar integer;

-- Capacidade máxima de pessoas da área comum; opcional
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS capacidade integer;
