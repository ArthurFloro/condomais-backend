-- Horário de funcionamento das áreas comuns (CONDO-08.2/8.3). Os dois nulos = sem restrição; fechamento antes da
-- abertura = passa da meia-noite (salão das 08h às 02h). Rode ANTES do merge: as colunas são opcionais, então a
-- versão atual continua funcionando, e a nova não sobe sem elas (spring.jpa.hibernate.ddl-auto=validate).
-- Pode rodar mais de uma vez.
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS horario_abertura time(0);
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS horario_fechamento time(0);
