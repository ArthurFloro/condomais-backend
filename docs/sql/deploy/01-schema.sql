-- Schema completo do Condo+ para um banco NOVO (ex.: Neon). Gerado pelo Hibernate a partir das
-- entidades em 2026-09-30 (schema-generation.scripts), então bate com o ddl-auto=validate de produção.
-- Rode uma vez, antes do primeiro deploy, e depois 02-dados-iniciais.sql.
-- Ao mudar entidades, gere de novo e acrescente scripts incrementais em docs/sql/.
BEGIN;

    create table apartamentos (
        condominio_id uuid not null,
        id uuid not null,
        torre_id uuid,
        numero varchar(50) not null,
        status varchar(50) not null,
        primary key (id)
    );

    create table areas_comuns (
        ativo boolean not null,
        exige_aprovacao boolean not null,
        reservavel boolean not null,
        condominio_id uuid not null,
        id uuid not null,
        descricao TEXT,
        nome varchar(255) not null,
        regras TEXT,
        primary key (id)
    );

    create table avisos (
        data_validade date,
        created_at timestamp(6) not null,
        autor_id uuid not null,
        condominio_id uuid not null,
        id uuid not null,
        torre_id uuid,
        prioridade varchar(50) not null,
        conteudo TEXT not null,
        titulo varchar(255) not null,
        primary key (id)
    );

    create table categorias_chamados (
        condominio_id uuid not null,
        id uuid not null,
        nome varchar(255) not null,
        primary key (id)
    );

    create table chamados (
        data_previsao_atendimento date,
        data_abertura timestamp(6) not null,
        data_encerramento timestamp(6),
        sla_prazo timestamp(6),
        apartamento_id uuid,
        categoria_id uuid not null,
        condominio_id uuid not null,
        id uuid not null,
        solicitante_id uuid not null,
        prioridade varchar(50) not null,
        status varchar(50) not null,
        contato_externo varchar(255),
        descricao TEXT not null,
        imagem_url varchar(255),
        responsavel_externo varchar(255),
        titulo varchar(255) not null,
        primary key (id)
    );

    create table condominios (
        created_at timestamp(6),
        id uuid not null,
        cnpj varchar(18) not null unique,
        status varchar(50) not null,
        nome varchar(255) not null,
        primary key (id)
    );

    create table encomendas (
        data_hora_recebimento timestamp(6) not null,
        data_hora_retirada timestamp(6),
        apartamento_id uuid not null,
        condominio_id uuid not null,
        id uuid not null,
        morador_id uuid,
        porteiro_recebimento_id uuid not null,
        porteiro_retirada_id uuid,
        status varchar(50) not null,
        codigo_identificacao varchar(255) not null,
        observacao TEXT,
        primary key (id)
    );

    create table historico_chamados (
        data_hora timestamp(6) not null,
        chamado_id uuid not null,
        id uuid not null,
        usuario_id uuid,
        acao varchar(255) not null,
        comentario TEXT,
        primary key (id)
    );

    create table reservas (
        data_reserva date not null,
        horario_fim time(0) not null,
        horario_inicio time(0) not null,
        aprovador_id uuid,
        area_id uuid not null,
        condominio_id uuid not null,
        id uuid not null,
        morador_id uuid not null,
        status varchar(50) not null,
        primary key (id)
    );

    create table torres (
        condominio_id uuid not null,
        id uuid not null,
        nome varchar(255) not null,
        primary key (id)
    );

    create table usuarios (
        cpf varchar(14) not null unique,
        apartamento_id uuid,
        condominio_id uuid not null,
        id uuid not null,
        status varchar(20),
        telefone varchar(20),
        vinculo varchar(20),
        perfil varchar(50) not null,
        email varchar(255),
        nome varchar(255) not null,
        senha varchar(255),
        primary key (id)
    );

    create table usuarios_apartamentos (
        apartamento_id uuid not null,
        id uuid not null,
        usuario_id uuid not null,
        status_aprovacao varchar(50) not null,
        tipo_vinculo varchar(50) not null,
        primary key (id)
    );

    create table visitantes (
        cpf varchar(14) not null unique,
        id uuid not null,
        telefone varchar(20),
        nome varchar(255) not null,
        primary key (id)
    );

    create table visitas (
        data_prevista date,
        horario_previsto time(0),
        data_hora_entrada timestamp(6),
        data_hora_saida timestamp(6),
        apartamento_id uuid not null,
        condominio_id uuid not null,
        id uuid not null,
        morador_id uuid,
        porteiro_entrada_id uuid,
        porteiro_saida_id uuid,
        visitante_id uuid not null,
        status varchar(50) not null,
        observacao TEXT,
        primary key (id)
    );

    alter table if exists apartamentos 
       add constraint FKiu4ev64hnibdtsnapdwy7hypv 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists apartamentos 
       add constraint FKsmaioumkgh3mx6j1fmp0mcfll 
       foreign key (torre_id) 
       references torres;

    alter table if exists areas_comuns 
       add constraint FK51o0xys1vgdiubtr2rjbvim4q 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists avisos 
       add constraint FKkb8heyukofolk2lek3w4u941a 
       foreign key (autor_id) 
       references usuarios;

    alter table if exists avisos 
       add constraint FK6yv661vrgoojdv5j3gkuq7mep 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists avisos 
       add constraint FK4lnwnhuix5vak92s9hr1pk4oc 
       foreign key (torre_id) 
       references torres;

    alter table if exists categorias_chamados 
       add constraint FKnc9otcqkr2h565uooil6j2pr5 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists chamados 
       add constraint FK23m4eo3t6dyek4ffrwv0awb9m 
       foreign key (apartamento_id) 
       references apartamentos;

    alter table if exists chamados 
       add constraint FKwuka4124dqsl9fq2mb5wq3tt 
       foreign key (categoria_id) 
       references categorias_chamados;

    alter table if exists chamados 
       add constraint FKqvtr2m3c28ahp0gqakhnr5hnu 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists chamados 
       add constraint FKnyqu3b6ka39lw69my5intk2lv 
       foreign key (solicitante_id) 
       references usuarios;

    alter table if exists encomendas 
       add constraint FKqcxr5mm6t8j4c6kcfg32u7i9j 
       foreign key (apartamento_id) 
       references apartamentos;

    alter table if exists encomendas 
       add constraint FKg834pnly34l4wxad83htfo50u 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists encomendas 
       add constraint FKl2gwly153uxj40v7xq51t9e4s 
       foreign key (morador_id) 
       references usuarios;

    alter table if exists encomendas 
       add constraint FKl8c6xo5xst1kavut0qiakxkuk 
       foreign key (porteiro_recebimento_id) 
       references usuarios;

    alter table if exists encomendas 
       add constraint FKlgxvkacc22upi3rao8te2yije 
       foreign key (porteiro_retirada_id) 
       references usuarios;

    alter table if exists historico_chamados 
       add constraint FK5e91j7f5d0e2yuwadmsge476y 
       foreign key (chamado_id) 
       references chamados;

    alter table if exists historico_chamados 
       add constraint FK40w8rljjfbyiaf0iyqfqlr6nr 
       foreign key (usuario_id) 
       references usuarios;

    alter table if exists reservas 
       add constraint FK1wxm8t44lyl5g3lnnos15obw5 
       foreign key (aprovador_id) 
       references usuarios;

    alter table if exists reservas 
       add constraint FK16a1gdswg0sgkyafppmrm0gpe 
       foreign key (area_id) 
       references areas_comuns;

    alter table if exists reservas 
       add constraint FKgbafcrgwj2f8etsg2ejtn21m 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists reservas 
       add constraint FKd7hduty87tcpynjqc0i9qq7cg 
       foreign key (morador_id) 
       references usuarios;

    alter table if exists torres 
       add constraint FKj5da4f752s88dw97ndvlup42w 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists usuarios 
       add constraint FKfrn2j7149s8pd6u0l7fak9mds 
       foreign key (apartamento_id) 
       references apartamentos;

    alter table if exists usuarios 
       add constraint FK248flx1nvbityq5113lbpyjul 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists usuarios_apartamentos 
       add constraint FKggk1x2b83u64rvrj8hancnoin 
       foreign key (apartamento_id) 
       references apartamentos;

    alter table if exists usuarios_apartamentos 
       add constraint FKqpu4xpfdxsc7o1voormh7y7er 
       foreign key (usuario_id) 
       references usuarios;

    alter table if exists visitas 
       add constraint FKdi8s9wlf8u70whxvmbbjval5 
       foreign key (apartamento_id) 
       references apartamentos;

    alter table if exists visitas 
       add constraint FK73jowtondvnohlw8og0wq89br 
       foreign key (condominio_id) 
       references condominios;

    alter table if exists visitas 
       add constraint FKekknpoani9a1adwq8xjdjvflv 
       foreign key (morador_id) 
       references usuarios;

    alter table if exists visitas 
       add constraint FKsfxreuc8036c6qs65qwvnv6fd 
       foreign key (porteiro_entrada_id) 
       references usuarios;

    alter table if exists visitas 
       add constraint FK7dncfsra05ujr83tc969tje2c 
       foreign key (porteiro_saida_id) 
       references usuarios;

    alter table if exists visitas 
       add constraint FK7s7yqli278slodmwy1mtd1ki1 
       foreign key (visitante_id) 
       references visitantes;

COMMIT;
