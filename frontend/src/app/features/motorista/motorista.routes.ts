import { Routes } from '@angular/router';

/**
 * Molde do kit — features/carga/carga.routes.ts. 'novo' vem ANTES de ':id',
 * senao a rota de parametro captura a palavra "novo".
 */
export const rotas: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/motorista-lista.page').then((m) => m.MotoristaListaPage),
  },
  {
    path: 'novo',
    loadComponent: () =>
      import('./pages/motorista-form.page').then((m) => m.MotoristaFormPage),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/motorista-form.page').then((m) => m.MotoristaFormPage),
  },
];
