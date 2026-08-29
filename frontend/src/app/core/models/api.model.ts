/**
 * Espelho dos envelopes do backend (common/ApiResponse.java e PageResponse.java).
 *
 * Como TODA resposta da API tem o mesmo formato, o interceptor de erro
 * consegue ser generico e nenhum componente precisa adivinhar onde esta a
 * mensagem de falha.
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string | null;
  errors?: ErroDeCampo[] | null;
}

/** Erro por campo — alimenta a validacao inline do formulario. */
export interface ErroDeCampo {
  field: string;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Pagina vazia — evita `null` em template de listagem antes da 1a resposta. */
export function paginaVazia<T>(size = 20): PageResponse<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
}
