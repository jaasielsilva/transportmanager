/**
 * Model de Motorista. Espelho dos DTOs do backend (MotoristaDtos.java).
 * Nao existe empresaId aqui, de proposito: o tenant vem do token.
 */
export interface MotoristaResumo {
  id: number;
  nome: string;
  telefone: string | null;
  ativo: boolean;
}

export interface MotoristaDetalhe extends MotoristaResumo {
  cnh: string | null;
  email: string | null;
  usuarioId: number | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface MotoristaSalvar {
  nome: string;
  cnh: string | null;
  telefone: string | null;
  email: string | null;
  usuarioId: number | null;
  ativo: boolean;
}
