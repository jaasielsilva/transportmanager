-- Evolução do módulo de Cargas para transporte de verdade
-- Adiciona campos específicos de logística ao CRUD genérico existente

-- Adicionar campos específicos de transporte à tabela cargas
ALTER TABLE cargas
ADD COLUMN origem_endereco VARCHAR(255) NULL COMMENT 'Endereço de origem da carga',
ADD COLUMN origem_cidade VARCHAR(100) NULL COMMENT 'Cidade de origem',
ADD COLUMN origem_uf CHAR(2) NULL COMMENT 'UF de origem',
ADD COLUMN destino_endereco VARCHAR(255) NULL COMMENT 'Endereço de destino da carga',
ADD COLUMN destino_cidade VARCHAR(100) NULL COMMENT 'Cidade de destino',
ADD COLUMN destino_uf CHAR(2) NULL COMMENT 'UF de destino',
ADD COLUMN peso DECIMAL(10,2) NULL COMMENT 'Peso da carga em kg',
ADD COLUMN valor_frete DECIMAL(12,2) NULL COMMENT 'Valor do frete em R$',
ADD COLUMN status VARCHAR(20) NULL DEFAULT 'PENDENTE' COMMENT 'Status: PENDENTE, COLETADA, EM_TRANSITO, ENTREGUE, PROBLEMATICA, CANCELADA',
ADD COLUMN motorista_id BIGINT NULL COMMENT 'Motorista responsável pela carga',
ADD COLUMN cliente_id BIGINT NULL COMMENT 'Cliente da carga',
ADD COLUMN data_coleta DATETIME NULL COMMENT 'Data/hora prevista para coleta',
ADD COLUMN data_entrega_prevista DATETIME NULL COMMENT 'Data/hora prevista para entrega',
ADD COLUMN data_entrega_real DATETIME NULL COMMENT 'Data/hora real da entrega',
ADD COLUMN distancia_km INT NULL COMMENT 'Distância estimada em km',
ADD COLUMN tempo_estimado_minutos INT NULL COMMENT 'Tempo estimado em minutos';

-- Adicionar índices para as novas colunas de busca
CREATE INDEX idx_cargas_status ON cargas(status);
CREATE INDEX idx_cargas_motorista ON cargas(motorista_id);
CREATE INDEX idx_cargas_cliente ON cargas(cliente_id);
CREATE INDEX idx_cargas_data_entrega_prevista ON cargas(data_entrega_prevista);
CREATE INDEX idx_cargas_origem_destino ON cargas(origem_cidade, destino_cidade);

-- Adicionar índices compostos com empresa_id (padrão do kit)
CREATE INDEX idx_cargas_empresa_status ON cargas(empresa_id, status);
CREATE INDEX idx_cargas_empresa_motorista ON cargas(empresa_id, motorista_id);
CREATE INDEX idx_cargas_empresa_cliente ON cargas(empresa_id, cliente_id);

-- Adicionar constraint para o status (será validado pelo backend também)
ALTER TABLE cargas ADD CONSTRAINT chk_carga_status 
CHECK (status IN ('PENDENTE', 'COLETADA', 'EM_TRANSITO', 'ENTREGUE', 'PROBLEMATICA', 'CANCELADA'));

-- Adicionar foreign keys (serão criadas quando as tabelas motoristas e clientes existirem)
-- ALTER TABLE cargas ADD CONSTRAINT fk_carga_motorista FOREIGN KEY (motorista_id) REFERENCES motoristas(id);
-- ALTER TABLE cargas ADD CONSTRAINT fk_carga_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);

-- Atualizar a quota do plano para o novo contexto (cargas de transporte).
-- `codigo` fica na tabela planos — o UPDATE junta com ela (mesma fonte da V3).
UPDATE plano_limites pl
  JOIN planos p ON p.id = pl.plano_id
   SET pl.valor = CASE p.codigo
          WHEN 'TRIAL'  THEN 10
          WHEN 'BASICO' THEN 50
          WHEN 'PRO'    THEN 200
          ELSE pl.valor
        END
 WHERE pl.chave = 'MAX_CADASTROS'
   AND p.codigo IN ('TRIAL', 'BASICO', 'PRO');