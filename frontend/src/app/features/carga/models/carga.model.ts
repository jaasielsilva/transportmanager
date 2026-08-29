/**
 * Molde do kit — features/carga/models/carga.model.ts
 *
 * Espelho dos DTOs do backend (CargaDtos.java). Um tipo por uso, como
 * la: a lista carrega o que a tabela mostra, e nada mais.
 *
 * Nao existe empresaId aqui, e isso e proposital: o tenant vem do token. Se um
 * dia aparecer um empresaId no corpo de um request deste projeto, e bug.
 */
export interface CargaResumo {
  id: number;
  nome: string;
  email: string | null;
  telefone: string | null;
  ativo: boolean;
}

export interface CargaDetalhe extends CargaResumo {
  documento: string | null;
  observacao: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface CargaSalvar {
  nome: string;
  email: string | null;
  telefone: string | null;
  documento: string | null;
  observacao: string | null;
  ativo: boolean;
}
