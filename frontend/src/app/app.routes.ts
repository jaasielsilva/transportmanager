import { Routes } from '@angular/router';
import { AppShellComponent } from './core/layout/app-shell/app-shell.component';
import { assinaturaGuard, authGuard, moduloGuard, roleGuard } from './core/guards/auth.guard';

/**
 * Molde do kit — app.routes.ts
 *
 * Dois blocos, e a divisao entre eles e a arquitetura de acesso do produto:
 *
 *   1. rotas PUBLICAS (login, criar conta, recuperar senha, convite) — nunca
 *      passam por guard nenhum;
 *   2. tudo o mais vive dentro do AppShellComponent, atras do authGuard.
 *
 * Duas rotas ficam de proposito FORA do assinaturaGuard: /assinatura e /plano.
 * Cliente suspenso precisa conseguir chegar na tela de pagar — bloquear
 * billing transforma um atraso de cobranca em churn definitivo.
 *
 * Nada aqui protege dado. Guard e UX; quem autoriza e o backend.
 */
export const routes: Routes = [
  // --- Publicas ---
  {
    path: 'login',
    loadComponent: () => import('./features/auth/pages/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'criar-conta',
    loadComponent: () =>
      import('./features/auth/pages/criar-conta.page').then((m) => m.CriarContaPage),
  },
  {
    path: 'esqueci-senha',
    loadComponent: () =>
      import('./features/auth/pages/esqueci-senha.page').then((m) => m.EsqueciSenhaPage),
  },
  {
    path: 'redefinir-senha',
    data: { modo: 'reset' },
    loadComponent: () =>
      import('./features/auth/pages/definir-senha.page').then((m) => m.DefinirSenhaPage),
  },
  {
    path: 'aceitar-convite',
    data: { modo: 'convite' },
    loadComponent: () =>
      import('./features/auth/pages/definir-senha.page').then((m) => m.DefinirSenhaPage),
  },

  // --- Area autenticada ---
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        canActivate: [assinaturaGuard],
        loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
      },
      {
        path: 'cargas',
        // Assinatura ANTES de modulo, igual a ordem dos interceptors no
        // backend: quem esta em atraso vai para a tela de regularizar, nao
        // para a de upgrade.
        canActivate: [assinaturaGuard, moduloGuard('CADASTROS')],
        loadChildren: () =>
          import('./features/carga/carga.routes').then((m) => m.rotas),
      },
      {
        path: 'equipe',
        canActivate: [assinaturaGuard, roleGuard('TENANT_ADMIN')],
        loadComponent: () => import('./features/equipe/equipe.page').then((m) => m.EquipePage),
      },
      {
        path: 'motoristas',
        canActivate: [assinaturaGuard, moduloGuard('CADASTROS')],
        loadChildren: () =>
          import('./features/motorista/motorista.routes').then((m) => m.rotas),
      },
      // "Minhas entregas": tela do motorista logado para iniciar/ver o
      // rastreamento das cargas dele. Sem roleGuard: qualquer USER ve, ja que
      // e a tela dele (quem nao e motorista de nada so ve a lista vazia).
      {
        path: 'minhas-entregas',
        canActivate: [assinaturaGuard, moduloGuard('OPERACAO')],
        loadComponent: () =>
          import('./features/rastreamento/pages/minhas-entregas.page').then(
            (m) => m.MinhasEntregasPage,
          ),
      },
      // Sem guard nenhum alem do authGuard, de proposito: todo usuario
      // autenticado troca a propria senha, PLATFORM_ADMIN incluido. Sem
      // assinaturaGuard pelo mesmo motivo do /assinatura: o backend isenta
      // /api/v1/me, e trancar a troca de senha atras da fatura em dia tranca a
      // reacao a um vazamento.
      {
        path: 'minha-conta',
        loadComponent: () =>
          import('./features/conta/pages/alterar-senha.page').then((m) => m.AlterarSenhaPage),
      },

      // Sem assinaturaGuard, de proposito: e o caminho de volta de quem esta
      // bloqueado.
      {
        path: 'assinatura',
        loadComponent: () =>
          import('./features/billing/assinatura.page').then((m) => m.AssinaturaPage),
      },
      {
        path: 'plano',
        loadComponent: () => import('./features/billing/plano.page').then((m) => m.PlanoPage),
      },

      // Area do DONO do SaaS. Sem assinaturaGuard: quem opera a plataforma nao
      // pode ser bloqueado pela regua de cobranca de um cliente.
      {
        path: 'plataforma',
        canActivate: [roleGuard('PLATFORM_ADMIN')],
        loadChildren: () =>
          import('./features/platform/platform.routes').then((m) => m.rotas),
      },
    ],
  },

  { path: '**', redirectTo: '' },
];
