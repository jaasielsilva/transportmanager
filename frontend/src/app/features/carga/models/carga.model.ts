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
