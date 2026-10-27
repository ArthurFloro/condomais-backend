-- Corrige o status de unidades que têm morador ativo mas estão marcadas como livres.
-- Não muda o schema; pode rodar antes ou depois do merge, e mais de uma vez.
--
-- Até esta versão, vincular um morador não atualizava o status da unidade (o wizard cria todas como
-- LIVRE). Daqui em diante a API mantém o status sozinha; este script só acerta o que ficou para trás.
-- INATIVO e RESERVADO são definidos à mão e não são tocados. O caminho inverso (OCUPADO sem morador
-- cadastrado) também não é tocado: pode ser uma unidade ocupada por alguém que ainda não tem conta.

-- Antes, para conferir o que vai mudar:
-- SELECT a.id, a.numero, a.status FROM apartamentos a
-- WHERE upper(a.status) NOT IN ('OCUPADO', 'INATIVO', 'RESERVADO')
--   AND EXISTS (SELECT 1 FROM usuarios u WHERE u.apartamento_id = a.id AND coalesce(u.status, 'ATIVO') <> 'INATIVO');

UPDATE apartamentos a
SET status = 'OCUPADO'
WHERE upper(a.status) NOT IN ('OCUPADO', 'INATIVO', 'RESERVADO')
  AND EXISTS (SELECT 1 FROM usuarios u WHERE u.apartamento_id = a.id AND coalesce(u.status, 'ATIVO') <> 'INATIVO');
