/**
 * Rotulos e cores de status de carga, compartilhados entre lista e formulario.
 *
 * O enum do backend vive em maiusculas (PENDENTE, EM_TRANSITO...); aqui fica so
 * a apresentacao em pt-BR. As classes retornadas (`selo-*`) sao os selos
 * semanticos do styles.css — nada de cor solta no componente.
 */
export const ROTULO_STATUS: Record<string, string> = {
  PENDENTE: 'Pendente',
  COLETADA: 'Coletada',
  EM_TRANSITO: 'Em transito',
  ENTREGUE: 'Entregue',
  PROBLEMATICA: 'Problematica',
  CANCELADA: 'Cancelada',
};

export function rotuloStatus(status: string | null): string {
  return ROTULO_STATUS[status ?? ''] ?? status ?? '—';
}

export function classeStatus(status: string | null): string {
  switch (status) {
    case 'COLETADA':
      return 'selo-coletada';
    case 'EM_TRANSITO':
      return 'selo-em-transito';
    case 'ENTREGUE':
      return 'selo-entregue';
    case 'PROBLEMATICA':
      return 'selo-problematica';
    case 'CANCELADA':
      return 'selo-cancelada';
    default:
      return '';
  }
}

/**
 * Transicoes validas a partir de cada status — espelho da regra em
 * CargaService.validarTransicaoStatus. A UI so oferece o que o backend aceita;
 * quem bloqueia de verdade e o @PreAuthorize + validacao do service.
 * Entregue/Cancelada sao finais: lista vazia = sem transicao.
 */
export const STATUS_TRANSICOES: Record<string, string[]> = {
  PENDENTE: ['COLETADA', 'CANCELADA'],
  COLETADA: ['EM_TRANSITO', 'PROBLEMATICA', 'CANCELADA'],
  EM_TRANSITO: ['ENTREGUE', 'PROBLEMATICA'],
  PROBLEMATICA: ['EM_TRANSITO', 'CANCELADA'],
  ENTREGUE: [],
  CANCELADA: [],
};
