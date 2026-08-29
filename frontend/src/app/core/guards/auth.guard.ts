import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Molde do kit — core/guards/auth.guard.ts
 *
 * TODOS os guards deste arquivo apenas ROTEIAM. Nenhum deles protege dado:
 * quem protege e o backend (@PreAuthorize, @RequiresModule, interceptor de
 * assinatura). Guard e UX — evita que a pessoa chegue numa tela que so
 * mostraria erro.
 *
 * Quando a aplicacao sobe, o provideAppInitializer ja tentou restaurar a
 * sessao pelo cookie httpOnly. Aqui, portanto, "sem usuario" significa mesmo
 * "sem sessao" — e nao "ainda carregando".
 */
export const authGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.autenticado()) {
    return true;
  }
  // returnUrl: depois de entrar, a pessoa volta para onde queria ir. Sem isso
  // todo link compartilhado joga o usuario na home.
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: estado.url } });
};

/** Rota restrita a papeis. Ex.: `canActivate: [authGuard, roleGuard('TENANT_ADMIN')]` */
export const roleGuard = (...roles: string[]): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const toast = inject(ToastService);

    if (auth.temRole(...roles)) {
      return true;
    }
    toast.erro('Voce nao tem acesso a esta area.');
    return router.createUrlTree(['/inicio']);
  };
};

/**
 * Rota de modulo vendavel. Sem o modulo no plano, o cliente vai para a tela de
 * plano — que explica o que ele ganharia — em vez de tomar um 403 seco.
 */
export const moduloGuard = (modulo: string): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    // PLATFORM_ADMIN opera a plataforma e precisa enxergar tudo para dar suporte.
    if (auth.temModulo(modulo) || auth.temRole('PLATFORM_ADMIN')) {
      return true;
    }
    return router.createUrlTree(['/plano'], { queryParams: { modulo } });
  };
};

/**
 * Assinatura suspensa: so login, regularizacao e billing continuam de pe.
 * O nivel SOMENTE_LEITURA nao bloqueia rota — o cliente continua vendo os
 * dados dele, que e justamente o que evita o churn por susto.
 */
export const assinaturaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.nivelAcesso() === 'SUSPENSO' ? router.createUrlTree(['/assinatura']) : true;
};
