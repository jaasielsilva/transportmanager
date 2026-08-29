/**
 * Espelho de PlatformDtos.java. É a visão do DONO do SaaS: situação comercial
 * da base inteira, nunca conteúdo operacional dos clientes.
 *
 * Para ver o sistema pelos olhos de um cliente existe a impersonação — que é
 * auditada e não está implementada no kit.
 */
export interface Dashboard {
  /** Só assinaturas ACTIVE. Contar trial aqui é inflar o próprio número. */
  mrr: number;
  tenantsAtivos: number;
  tenantsEmTrial: number;
  tenantsEmAtraso: number;
  trialsExpirandoEm7Dias: number;
  canceladasNoMes: number;
  churnPercentual: number;
  ativacaoPercentual: number;
  /** Quanto do MRR está preso na régua de dunning. */
  receitaEmRisco: number;
  usoPorModulo: UsoModulo[];
}

export interface UsoModulo {
  modulo: string;
  tenants: number;
}

export interface TenantResumo {
  id: number;
  razaoSocial: string;
  plano: string;
  assinaturaStatus: string;
  dunningEtapa: number;
  trialExpiraEm: string | null;
  usuarios: number;
  ultimoAcesso: string | null;
}

export interface TenantDetalhe {
  id: number;
  razaoSocial: string;
  documento: string;
  plano: string;
  precoMensal: number | null;
  assinaturaStatus: string;
  dunningEtapa: number;
  nivelAcesso: string;
  pastDueDesde: string | null;
  trialExpiraEm: string | null;
  ativadaEm: string | null;
  purgeEm: string | null;
  usuarios: number;
  limites: LimiteConsumo[];
}

/** consumo = -1 quando nenhuma feature sabe medir essa chave (ver ConsumoDeQuota). */
export interface LimiteConsumo {
  chave: string;
  limite: number;
  consumo: number;
}

export interface DunningEvento {
  etapa: number;
  acao: string;
  detalhes: string | null;
  em: string;
}

export interface WebhookFalha {
  eventoId: string;
  tipo: string;
  erro: string | null;
  em: string;
}

export type SituacaoDeTenant =
  | ''
  | 'PAST_DUE'
  | 'TRIAL_EXPIRANDO'
  | 'TRIALING'
  | 'ACTIVE'
  | 'CANCELED';
