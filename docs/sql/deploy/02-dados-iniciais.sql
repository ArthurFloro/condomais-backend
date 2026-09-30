-- Dados mínimos para o primeiro acesso em um banco novo: o condomínio e o primeiro administrador.
-- Rode depois de 01-schema.sql. Troque os valores entre <...> antes de executar.
--
-- O administrador é criado SEM senha: ele ativa a conta em "Ativar minha conta" no front
-- (POST /auth/primeiro-acesso) com o CPF e define a própria senha. Depois disso, ele cadastra
-- torres, unidades e moradores pelas telas.
--
-- CPF no formato 000.000.000-00 (o login compara o valor exato).
BEGIN;

WITH novo_condominio AS (
    INSERT INTO condominios (id, nome, cnpj, status, created_at)
    VALUES (gen_random_uuid(), '<Nome do condomínio>', '<00.000.000/0000-00>', 'ATIVO', now())
    RETURNING id
)
INSERT INTO usuarios (id, nome, cpf, email, telefone, perfil, status, senha, condominio_id)
SELECT gen_random_uuid(), '<Nome do administrador>', '<000.000.000-00>', '<email@exemplo.com>', NULL, 'ADMIN', 'ATIVO', NULL, id
FROM novo_condominio;

COMMIT;
