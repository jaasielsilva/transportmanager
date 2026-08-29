import { Routes } from '@angular/router';

/**
 * Molde do kit — rotas da área do dono do SaaS.
 *
 * Elas ficam fora do `assinaturaGuard` de propósito: quem opera a plataforma
 * nunca pode ser bloqueado pela régua de cobrança de um cliente. O que protege
 * é o `roleGuard('PLATFORM_ADMIN')` no app.routes.ts — e, de verdade, o
 * `@PreAuthorize` na classe do PlatformController.
 */
export const rotas: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/painel.page').then((m) => m.PainelPage),
  },
  {
    path: 'tenants',
    loadComponent: () => import('./pages/tenants.page').then((m) => m.TenantsPage),
  },
  {
    path: 'tenants/:id',
    loadComponent: () => import('./pages/tenant-detalhe.page').then((m) => m.TenantDetalhePage),
  },
];
