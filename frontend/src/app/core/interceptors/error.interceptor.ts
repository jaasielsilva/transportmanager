import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Molde do kit — core/interceptors/error.interceptor.ts
 *
 * Traduz o envelope ApiResponse em toast e resolve o 401 com UM refresh, para
 * que o componente so precise tratar o caso de negocio dele.
 *
 * O 402 e o mais importante daqui: e a resposta de assinatura em atraso, e
 * leva direto para a tela de regularizacao. Se caisse no balde de erro
 * generico, o cliente veria "sem permissao" e abriria chamado de bug — quando
 * o que ele precisa e de um botao para pagar.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((erro: HttpErrorResponse) => {
      const mensagem = erro.error?.message as string | undefined;
      const ehRefresh = req.url.includes('/auth/refresh');

      // 401 no proprio refresh = sessao morta de vez; nao entrar em loop.
      if (erro.status === 401 && !ehRefresh) {
        return auth.refresh().pipe(
          switchMap(() =>
            next(
              req.clone({
                withCredentials: true,
                setHeaders: { Authorization: `Bearer ${auth.accessToken}` },
              }),
            ),
          ),
          catchError(() => {
            auth.limparSessao();
            router.navigate(['/login']);
            return throwError(() => erro);
          }),
        );
      }

      // O refresh e chamado em silencio pelo proprio app — ao abrir a pagina
      // (restaurarSessao, tentando reaproveitar o cookie) e aqui em cima, ao
      // reagir a um 401. QUALQUER visitante sem sessao ainda (o caso comum:
      // primeira visita, nunca logou) bate nesse endpoint e recebe 409
      // "sessao expirada" — nao e um erro para avisar, e o estado normal de
      // quem ainda nao entrou. Quem trata a falha e quem chamou:
      // restaurarSessao() engole via catchError proprio, e o bloco de 401
      // acima ja limpa a sessao e navega para /login. Toast aqui seria
      // alarme falso a cada carregamento de pagina.
      if (ehRefresh) {
        return throwError(() => erro);
      }

      switch (erro.status) {
        case 0:
          toast.erro('Sem conexao com o servidor. Verifique sua internet.');
          break;
        case 402:
          // Pagamento em atraso. Nao e erro de permissao nem bug: tem caminho
          // de saida, e ele e uma tela, nao uma mensagem.
          auth.recarregarUsuario().subscribe();
          router.navigate(['/assinatura']);
          break;
        case 403:
          // Falta de permissao (role) OU modulo fora do plano — a mensagem do
          // backend distingue os dois.
          toast.erro(mensagem ?? 'Voce nao tem permissao para esta acao.');
          break;
        case 404:
          toast.erro(mensagem ?? 'Registro nao encontrado.');
          break;
        case 409:
          // Inclui o limite de plano estourado, que e momento comercial: a
          // mensagem do backend ja vem com o caminho de upgrade.
          toast.aviso(mensagem ?? 'Operacao nao permitida.');
          break;
        case 400:
          // Erros por campo aparecem inline no formulario; o toast so resume.
          toast.erro(mensagem ?? 'Verifique os campos informados.');
          break;
        default:
          toast.erro(mensagem ?? 'Erro inesperado. Tente novamente.');
      }

      return throwError(() => erro);
    }),
  );
};
