/** Espelho de AuthDtos.UsuarioLogado / LoginResponse no backend. */

/** Derivado da regua de dunning — o front NUNCA calcula isto sozinho. */
export type NivelAcesso = 'NORMAL' | 'SOMENTE_LEITURA' | 'SUSPENSO';

export type AssinaturaStatus = 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';

export interface UsuarioLogado {
  id: number;
  nome: string;
  email: string;
  empresaId: number;
  empresa: string;
  /** PLATFORM_ADMIN | TENANT_ADMIN | USER */
  roles: string[];
  /** Modulos que o plano habilita — usados para ESCONDER menu, nunca para autorizar. */
  modulos: string[];
  assinaturaStatus: AssinaturaStatus;
  nivelAcesso: NivelAcesso;
}

export interface LoginResponse {
  /** Vive so em memoria. Em localStorage, um XSS vira conta roubada. */
  accessToken: string;
  expiraEmSegundos: number;
  usuario: UsuarioLogado;
}
