-- Setup Wizard: estado da configuração inicial, endereço do condomínio e unicidade de torres e unidades.
-- Rode no banco ANTES do merge da versão que traz POST /condominios/configuracao-inicial: com
-- spring.jpa.hibernate.ddl-auto=validate, a versão nova não sobe sem as colunas. É compatível com a anterior.

-- 1) Condomínio: endereço e estado da configuração. Os condomínios que já existem foram montados à mão,
--    então entram como CONCLUIDA. Para um deles passar pelo wizard, troque para PENDENTE.
ALTER TABLE condominios ADD COLUMN IF NOT EXISTS endereco varchar(255);
ALTER TABLE condominios ADD COLUMN IF NOT EXISTS cidade varchar(100);
ALTER TABLE condominios ADD COLUMN IF NOT EXISTS estado varchar(2);
ALTER TABLE condominios ADD COLUMN IF NOT EXISTS configuracao_inicial varchar(20) NOT NULL DEFAULT 'CONCLUIDA';
-- O DEFAULT fica: a versão anterior da API, que ainda não conhece a coluna, continua conseguindo cadastrar
-- condomínios entre esta migração e o deploy. A versão nova sempre grava o valor (PENDENTE pelo MASTER).

-- 2) Unicidade. Se algum CREATE falhar, já há duplicatas: liste-as com as consultas abaixo e corrija antes.
--    SELECT condominio_id, lower(nome), count(*) FROM torres GROUP BY 1, 2 HAVING count(*) > 1;
--    SELECT torre_id, numero, count(*) FROM apartamentos WHERE torre_id IS NOT NULL GROUP BY 1, 2 HAVING count(*) > 1;
--    SELECT condominio_id, numero, count(*) FROM apartamentos WHERE torre_id IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
-- Torre: nome único no condomínio, sem diferenciar maiúsculas
CREATE UNIQUE INDEX IF NOT EXISTS torres_nome_unico ON torres (condominio_id, lower(nome));
-- Unidade: número único na torre; sem torre (edifício pequeno), único entre as sem torre do condomínio
CREATE UNIQUE INDEX IF NOT EXISTS apartamentos_numero_unico_na_torre ON apartamentos (torre_id, numero) WHERE torre_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS apartamentos_numero_unico_sem_torre ON apartamentos (condominio_id, numero) WHERE torre_id IS NULL;
