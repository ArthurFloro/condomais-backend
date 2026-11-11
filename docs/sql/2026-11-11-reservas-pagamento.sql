-- Pagamento manual de reservas (CONDO-08.5): taxa e instruções na área comum; na reserva, o valor cobrado e
-- quem confirmou o pagamento. Rode ANTES do merge: as colunas são opcionais, então a versão atual continua
-- funcionando, e a nova não sobe sem elas (spring.jpa.hibernate.ddl-auto=validate). Pode rodar mais de uma vez.

-- 1) Área comum: taxa (nula = gratuita) e instruções informativas de pagamento
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS taxa numeric(10,2);
ALTER TABLE areas_comuns ADD COLUMN IF NOT EXISTS instrucoes_pagamento text;

-- 2) Reserva: taxa do momento do pedido e a confirmação do pagamento pelo admin
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS valor_taxa numeric(10,2);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS pagamento_confirmado_por_id uuid;
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS pagamento_confirmado_em timestamp(6);

ALTER TABLE reservas DROP CONSTRAINT IF EXISTS fk_reservas_pagamento_confirmado_por;
ALTER TABLE reservas ADD CONSTRAINT fk_reservas_pagamento_confirmado_por
    FOREIGN KEY (pagamento_confirmado_por_id) REFERENCES usuarios (id);
