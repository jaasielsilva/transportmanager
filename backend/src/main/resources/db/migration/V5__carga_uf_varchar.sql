-- V4 criou origem_uf/destino_uf como CHAR(2), mas a entity Carga mapeia
-- @Column(length = 2) -> varchar(2). O Hibernate valida o tipo exato na subida
-- e recusa CHAR ("wrong column type"). Converter as colunas corrige a validacao
-- em qualquer banco: o local (que ja aplicou a V4) e os novos (CI/homolog).

ALTER TABLE cargas
    MODIFY COLUMN origem_uf VARCHAR(2) NULL COMMENT 'UF de origem',
    MODIFY COLUMN destino_uf VARCHAR(2) NULL COMMENT 'UF de destino';
