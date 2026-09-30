-- Cadastro de usuários (/usuarios): status, vínculo com unidade e tipo de vínculo.
-- Rode antes de subir a aplicação em bancos com ddl-auto=validate.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ATIVO';
UPDATE usuarios SET status = 'ATIVO' WHERE status IS NULL;

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS apartamento_id UUID REFERENCES apartamentos(id);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS vinculo VARCHAR(20);

-- Padroniza perfis antigos para os valores aceitos pelo cadastro (ADMIN, PORTARIA, MORADOR).
UPDATE usuarios SET perfil = 'ADMIN' WHERE UPPER(perfil) IN ('ADMIN', 'ADMINISTRADOR');
UPDATE usuarios SET perfil = 'PORTARIA' WHERE UPPER(perfil) IN ('PORTARIA', 'PORTEIRO');
UPDATE usuarios SET perfil = 'MORADOR' WHERE UPPER(perfil) = 'MORADOR';
