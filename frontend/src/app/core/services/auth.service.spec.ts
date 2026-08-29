import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Molde do kit — o teste que protege as duas decisoes de seguranca da sessao.
 *
 * Sao regressoes silenciosas: nada quebra na tela quando alguem "melhora" o
 * AuthService guardando o token em localStorage para sobreviver ao F5, ou
 * remove o single-flight do refresh por parecer complicado. O estrago aparece
 * depois, em producao, e com cara de outra coisa.
 */
describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const resposta = {
    success: true,
    data: {
      accessToken: 'token-de-teste',
      expiraEmSegundos: 900,
      usuario: {
        id: 1,
        nome: 'Maria',
        email: 'maria@exemplo.com',
        empresaId: 10,
        empresa: 'Empresa Teste',
        roles: ['TENANT_ADMIN'],
        modulos: ['CADASTROS'],
        assinaturaStatus: 'ACTIVE',
        nivelAcesso: 'NORMAL',
      },
    },
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('guarda o access token so em memoria — nunca em localStorage', () => {
    service.login('maria@exemplo.com', 'segredo123').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(resposta);

    expect(service.accessToken).toBe('token-de-teste');
    expect(service.autenticado()).toBe(true);
    // Se um dia isto falhar, um XSS passa a valer uma conta.
    expect(JSON.stringify(localStorage)).not.toContain('token-de-teste');
  });

  it('dispara UM unico refresh mesmo com varias chamadas simultaneas', () => {
    service.refresh().subscribe();
    service.refresh().subscribe();
    service.refresh().subscribe();

    // Dois refresh em paralelo fariam o segundo chegar com um token ja
    // rotacionado; o backend trataria como reuso e derrubaria a sessao.
    const requisicoes = http.match(`${environment.apiUrl}/auth/refresh`);
    expect(requisicoes).toHaveLength(1);
    requisicoes[0].flush(resposta);
  });

  it('limparSessao apaga token e usuario', () => {
    service.login('maria@exemplo.com', 'segredo123').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(resposta);

    service.limparSessao();

    expect(service.accessToken).toBeNull();
    expect(service.autenticado()).toBe(false);
  });

  it('temModulo responde pelo que o plano habilita', () => {
    service.login('maria@exemplo.com', 'segredo123').subscribe();
    http.expectOne(`${environment.apiUrl}/auth/login`).flush(resposta);

    expect(service.temModulo('CADASTROS')).toBe(true);
    expect(service.temModulo('RELATORIOS')).toBe(false);
  });
});
