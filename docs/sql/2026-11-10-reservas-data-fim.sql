-- Reserva que passa da meia-noite (22h às 02h): a reserva ganha o dia em que termina.
-- Em duas etapas, porque a versão antiga do backend ainda grava reservas sem data_fim até o deploy terminar
-- (ela continua funcionando com a coluna nula; a nova trata data_fim nula como o próprio dia).

-- 1) ANTES do merge: cria a coluna (nula) e preenche as reservas existentes, que terminam no mesmo dia.
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS data_fim date;
UPDATE reservas SET data_fim = data_reserva WHERE data_fim IS NULL;

-- 2) DEPOIS que a versão nova estiver no ar: preenche o que a versão antiga gravou no meio do caminho
--    e torna a coluna obrigatória.
-- ALTER TABLE reservas ALTER COLUMN data_fim SET NOT NULL;
-- UPDATE reservas SET data_fim = data_reserva WHERE data_fim IS NULL;
