/**
 * Model de Cargas de Transporte - Evoluído do CRUD genérico do kit
 *
 * Espelho dos DTOs do backend (CargaDtos.java). Um tipo por uso, como
 * lá: a lista carrega o que a tabela mostra, e nada mais.
 *
 * Não existe empresaId aqui, e isso é proposital: o tenant vem do token. Se um
 * dia aparecer um empresaId no corpo de um request deste projeto, é bug.
 */
export interface CargaResumo {
  id: number;
  nome: string;
  status: string;
  origemCidade: string | null;
  origemUf: string | null;
  destinoCidade: string | null;
  destinoUf: string | null;
  valorFrete: number | null;
  ativo: boolean;
}

export interface CargaDetalhe extends CargaResumo {
  email: string | null;
  telefone: string | null;
  documento: string | null;
  observacao: string | null;
  criadoEm: string;
  atualizadoEm: string;
  // Campos específicos de transporte
  origemEndereco: string | null;
  destinoEndereco: string | null;
  peso: number | null;
  motoristaId: number | null;
  clienteId: number | null;
  dataColeta: string | null;
  dataEntregaPrevista: string | null;
  dataEntregaReal: string | null;
  distanciaKm: number | null;
  tempoEstimadoMinutos: number | null;
}

export interface CargaSalvar {
  // Campos originais do CRUD genérico
  nome: string;
  email: string | null;
  telefone: string | null;
  documento: string | null;
  observacao: string | null;
  ativo: boolean;
  // Campos específicos de transporte
  origemEndereco: string | null;
  origemCidade: string;
  origemUf: string | null;
  destinoEndereco: string | null;
  destinoCidade: string;
  destinoUf: string | null;
  peso: number | null;
  valorFrete: number | null;
  status: string | null;
  motoristaId: number | null;
  clienteId: number | null;
  dataColeta: string | null;
  dataEntregaPrevista: string | null;
  dataEntregaReal: string | null;
  distanciaKm: number | null;
  tempoEstimadoMinutos: number | null;
}

export interface CargaAtualizarStatus {
  status: string;
}

/**
 * Entrada do endpoint helper `POST /cargas/calcular-rota`. So os enderecos que
 * o Google usa para tracar a rota — nada de id nem tenant (vem do token).
 */
export interface CargaCalcularRota {
  origemCidade: string;
  origemUf: string | null;
  origemEndereco: string | null;
  destinoCidade: string;
  destinoUf: string | null;
  destinoEndereco: string | null;
}

/**
 * Saida do mesmo endpoint. Nulls sao intencionais: o Google pode nao achar rota
 * (ZERO_RESULTS) e isso nao e erro — e o form que decide o que fazer com o campo
 * em branco (aviso sem sobrescrever o que ja esta la).
 */
export interface CargaEstimativaRota {
  distanciaKm: number | null;
  tempoEstimadoMinutos: number | null;
}

/**
 * Endereco resolvido por CEP (`GET /ceps/{cep}` → ViaCEP no backend). Usado no
 * autofill do form de carga: o CEP preenche endereco/cidade/UF para o "Calcular
 * rota" tracar com precisao. Nao persiste nada — o CEP e so conveniencia de
 * preenchimento.
 */
export interface CepDados {
  cep: string;
  logradouro: string | null;
  bairro: string | null;
  cidade: string;
  uf: string;
}

