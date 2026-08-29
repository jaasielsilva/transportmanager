import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';

// pt-BR em data, numero e moeda desde o primeiro dia: trocar isso depois
// significa revisar toda tela que exibe valor ou data.
registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),

    // A ORDEM DOS INTERCEPTORS IMPORTA: o de autenticacao anexa o token e o de
    // erro envolve a chamada ja autenticada. Invertidos, o retry apos o
    // refresh sairia sem o token novo — e o 401 viraria um loop.
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),

    { provide: LOCALE_ID, useValue: 'pt-BR' },

    // Restaura a sessao pelo cookie httpOnly ANTES da primeira rota abrir.
    // Sem isto, todo F5 cai no login mesmo com sessao valida — o access token
    // vive so em memoria e se perde no reload.
    provideAppInitializer(() => inject(AuthService).restaurarSessao()),
  ],
};
