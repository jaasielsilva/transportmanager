import { describe, expect, it } from 'vitest';
import { ItemDeMenu, itensVisiveis } from './nav.config';

/**
 * O menu e a primeira coisa que o cliente ve. Mostrar item de modulo que ele
 * nao comprou e propaganda de recurso que vai dar 403 — pior que nao mostrar.
 */
describe('itensVisiveis', () => {
  const menu: ItemDeMenu[] = [
    { rota: '/inicio', titulo: 'Inicio', icone: '' },
    { rota: '/cadastros', titulo: 'Cadastros', icone: '', modulo: 'CADASTROS' },
    { rota: '/relatorios', titulo: 'Relatorios', icone: '', modulo: 'RELATORIOS' },
    { rota: '/equipe', titulo: 'Equipe', icone: '', roles: ['TENANT_ADMIN'] },
  ];

  it('esconde modulo que o plano nao inclui', () => {
    const visiveis = itensVisiveis(menu, ['USER'], ['CADASTROS']);

    expect(visiveis.map((i) => i.rota)).toEqual(['/inicio', '/cadastros']);
  });

  it('esconde item restrito a outro papel', () => {
    const visiveis = itensVisiveis(menu, ['USER'], ['CADASTROS', 'RELATORIOS']);

    expect(visiveis.some((i) => i.rota === '/equipe')).toBe(false);
  });

  it('mostra o item de admin para o TENANT_ADMIN', () => {
    const visiveis = itensVisiveis(menu, ['TENANT_ADMIN'], []);

    expect(visiveis.map((i) => i.rota)).toEqual(['/inicio', '/equipe']);
  });

  it('PLATFORM_ADMIN enxerga tudo — ele da suporte a clientes de todo plano', () => {
    const visiveis = itensVisiveis(menu, ['PLATFORM_ADMIN'], []);

    expect(visiveis).toHaveLength(menu.length);
  });
});
