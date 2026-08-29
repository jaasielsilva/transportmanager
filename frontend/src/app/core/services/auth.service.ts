import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api.model';
import { LoginResponse, NivelAcesso, UsuarioLogado } from '../models/sessao.model';

/**
 * Molde do kit — core/services/auth.service.ts
 *
 * Dono da sessao. Tres decisoes aqui nao sao estilo, sao seguranca:
 *
 * 1. O access token vive em um campo privado — NUNCA em localStorage ou
 *    sessionStorage. Storage e legivel por qualquer script da pagina: um XSS
 *    (numa dependencia, num anuncio, num trecho colado) le o token e a conta
 *    esta perdida.
 *
 * 2. O refresh token nem passa por aqui: ele viaja em cookie httpOnly, que o
 *    JavaScript nao consegue ler. E por isso que recarregar a pagina (F5) nao
 *    desloga: o token de memoria some, mas restaurarSessao() troca o cookie
 *    por um access token novo.
 *
 * 3. refresh() e single-flight. Sem isso, tres chamadas paralelas que tomam
 *    401 disparam tres refresh; como o backend ROTACIONA o refresh token, o
 *    segundo chega com um token ja usado, o backend trata como reuso (o sinal
 *    classico de roubo de token) e revoga a sessao inteira — o usuario e
 *    deslogado sozinho, do nada, e ninguem consegue reproduzir.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  private token: string | null = null;
  private refreshEmAndamento?: Observable<LoginResponse>;

  /** Fonte unica de "quem esta logado" para toda a aplicacao. */
  readonly usuario = signal<UsuarioLogado | null>(null);

  readonly autenticado = computed(() => this.usuario() !== null);
  readonly nivelAcesso = computed<NivelAcesso>(() => this.usuario()?.nivelAcesso ?? 'NORMAL');
  /** Em atraso avancado o cliente ve os dados, mas nao grava. */
  readonly somenteLeitura = computed(() => this.nivelAcesso() !== 'NORMAL');

  get accessToken(): string | null {
    return this.token;
  }

  temRole(...roles: string[]): boolean {
    const doUsuario = this.usuario()?.roles ?? [];
    return roles.some((r) => doUsuario.includes(r));
  }

  /**
   * Serve para ESCONDER o que o plano nao inclui. Quem bloqueia e o backend
   * (@RequiresModule): esconder no menu nao impede ninguem de digitar a URL.
   */
  temModulo(modulo: string): boolean {
    return (this.usuario()?.modulos ?? []).includes(modulo);
  }

  login(email: string, senha: string): Observable<UsuarioLogado> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.api}/auth/login`, { email, senha })
      .pipe(
        map((r) => r.data),
        tap((dados) => this.aplicarSessao(dados)),
        map((dados) => dados.usuario),
      );
  }

  logout(): Observable<unknown> {
    // Sai da sessao do lado do servidor tambem: sem isso o refresh token
    // continua valido no banco ate expirar, e "sair" vira so aparencia.
    return this.http.post(`${this.api}/auth/logout`, {}).pipe(
      catchError(() => of(null)),
      tap(() => this.limparSessao()),
    );
  }

  refresh(): Observable<LoginResponse> {
    if (!this.refreshEmAndamento) {
      this.refreshEmAndamento = this.http
        .post<ApiResponse<LoginResponse>>(`${this.api}/auth/refresh`, {})
        .pipe(
          map((r) => r.data),
          tap((dados) => this.aplicarSessao(dados)),
          finalize(() => (this.refreshEmAndamento = undefined)),
          shareReplay(1),
        );
    }
    return this.refreshEmAndamento;
  }

  /**
   * Roda uma vez na subida da aplicacao (provideAppInitializer).
   *
   * Sem isto, todo F5 cai na tela de login mesmo com sessao valida — o token
   * de memoria se perde no reload e o cookie de refresh, que continua la,
   * nunca seria usado.
   */
  restaurarSessao(): Observable<unknown> {
    return this.refresh().pipe(catchError(() => of(null)));
  }

  /** Recarrega o contexto do usuario (ex.: depois de mudar de plano). */
  recarregarUsuario(): Observable<UsuarioLogado | null> {
    return this.http.get<ApiResponse<UsuarioLogado>>(`${this.api}/me`).pipe(
      map((r) => r.data),
      tap((usuario) => this.usuario.set(usuario)),
      catchError(() => of(null)),
    );
  }

  limparSessao(): void {
    this.token = null;
    this.usuario.set(null);
  }

  private aplicarSessao(dados: LoginResponse): void {
    this.token = dados.accessToken;
    this.usuario.set(dados.usuario);
  }
}
