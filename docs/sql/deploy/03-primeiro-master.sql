-- Primeiro usuário MASTER (donos do Condo+), que cadastra os condomínios e os administradores deles
-- pela área /plataforma. Rode depois de 01-schema.sql. Troque os valores entre <...> antes de executar.
--
-- O MASTER não pertence a nenhum condomínio (condominio_id NULL) e é criado SEM senha: ele ativa a
-- conta em "Ativar minha conta" no front (POST /auth/primeiro-acesso) com o CPF.
--
-- CPF no formato 000.000.000-00 (o login compara o valor exato).
INSERT INTO usuarios (id, nome, cpf, email, telefone, perfil, status, senha, condominio_id)
VALUES (gen_random_uuid(), '<Nome>', '<000.000.000-00>', '<email@exemplo.com>', NULL, 'MASTER', 'ATIVO', NULL, NULL);
