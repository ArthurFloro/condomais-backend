-- Primeiro acesso (POST /auth/primeiro-acesso) só cria senha para usuário sem senha.
-- A Administração pré-cadastra o usuário com senha NULL; o próprio usuário define a senha depois.
-- O ddl-auto (validate ou update) não remove NOT NULL, então rode manualmente em cada banco.
ALTER TABLE usuarios ALTER COLUMN senha DROP NOT NULL;

-- Para liberar o primeiro acesso de um usuário já existente (ex.: no banco local de desenvolvimento):
-- UPDATE usuarios SET senha = NULL WHERE cpf = '<cpf>';
