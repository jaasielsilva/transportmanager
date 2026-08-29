import { Routes } from '@angular/router';

/**
 * Molde do kit — rotas da feature, carregadas sob demanda.
 *
 * Cada feature exporta as proprias rotas e o app.routes.ts so aponta para ca
 * (loadChildren). E isso que mantem o bundle inicial pequeno: quem nunca abre
 * este modulo nunca baixa o codigo dele.
 *
 * A ordem importa: 'novo' precisa vir ANTES de ':id', senao a rota de
 * parametro captura a palavra "novo" e a tela de cadastro tenta carregar um
 * registro chamado novo.
 */
export const rotas: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/carga-lista.page').then((m) => m.CargaListaPage),
  },
  {
    path: 'novo',
    loadComponent: () =>
      import('./pages/carga-form.page').then((m) => m.CargaFormPage),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/carga-form.page').then((m) => m.CargaFormPage),
  },
];
