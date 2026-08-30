/**
 * Molde do kit — core/navigation/nav.config.ts
 *
 * O menu inteiro em um lugar so. Cada item declara o que exige: `modulo` (do
 * plano contratado) e/ou `roles`.
 *
 * O ganho de manter isto declarativo: quando nasce um modulo novo, o menu, o
 * guard da rota e a tabela "Modulos x planos" da ARQUITETURA falam do MESMO
 * codigo (o enum Modulo do backend). Menu montado com @if espalhado pelo
 * template e o caminho para o cliente ver a aba de um modulo que ele nao
 * comprou.
 */
export interface ItemDeMenu {
  rota: string;
  titulo: string;
  /** Trocar por icone de verdade (SVG) no seu projeto. */
  icone: string;
  /** Codigo do enum Modulo no backend. Ausente = sempre visivel. */
  modulo?: string;
  /** Ausente = qualquer usuario autenticado. */
  roles?: string[];
}

export const MENU: ItemDeMenu[] = [
  { rota: '/inicio', titulo: 'Inicio', icone: '■' },
  { rota: '/cargas', titulo: 'Cargas', icone: '●', modulo: 'CADASTROS' },
  {
    rota: '/motoristas',
    titulo: 'Motoristas',
    icone: '■',
    modulo: 'CADASTROS',
    roles: ['TENANT_ADMIN'],
  },
  // Sem `roles`: e a tela do motorista logado, qualquer USER autenticado ve.
  { rota: '/minhas-entregas', titulo: 'Minhas entregas', icone: '▶', modulo: 'OPERACAO' },
  // Sem `roles` de proposito: e o unico item que serve a TODA role,
  // PLATFORM_ADMIN incluido — trocar a propria senha nao e privilegio de perfil.
  { rota: '/minha-conta', titulo: 'Minha conta', icone: '◐' },
  { rota: '/equipe', titulo: 'Equipe', icone: '▲', roles: ['TENANT_ADMIN'] },
  { rota: '/plano', titulo: 'Plano e cobranca', icone: '◆', roles: ['TENANT_ADMIN'] },
  // Area do dono do SaaS — nao aparece para nenhum cliente.
  { rota: '/plataforma', titulo: 'Plataforma', icone: '★', roles: ['PLATFORM_ADMIN'] },
];

/**
 * Funcao pura: da para testar sem TestBed, sem HTTP e sem navegador — e o
 * teste que garante que ninguem enxerga item de modulo nao contratado.
 */
export function itensVisiveis(
  itens: ItemDeMenu[],
  roles: string[],
  modulos: string[],
): ItemDeMenu[] {
  // Dono do SaaS ve tudo: ele da suporte a clientes de todos os planos.
  const ehPlatformAdmin = roles.includes('PLATFORM_ADMIN');

  return itens.filter((item) => {
    const temModulo = ehPlatformAdmin || !item.modulo || modulos.includes(item.modulo);
    const temRole = ehPlatformAdmin || !item.roles || item.roles.some((r) => roles.includes(r));
    return temModulo && temRole;
  });
}
