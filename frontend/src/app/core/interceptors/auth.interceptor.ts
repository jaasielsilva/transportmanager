import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Molde do kit — core/interceptors/auth.interceptor.ts
 *
 * Anexa o access token (que vive so em memoria) e manda o cookie httpOnly do
 * refresh viajar junto.
 *
 * withCredentials: sem ele o cookie do refresh nao e enviado em requisicao
 * cross-origin — e em desenvolvimento o front (4200) e o back (8080) SAO
 * origens diferentes. O sintoma e cruel: funciona em producao, onde tudo passa
 * pelo mesmo Nginx, e so quebra na maquina do dev.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken;

  return next(
    req.clone({
      withCredentials: true,
      setHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    }),
  );
};
