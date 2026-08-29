import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../../environments/environment';
import { CargaService } from './carga.service';

/**
 * Protege o contrato do endpoint helper de rota: o service aponta para
 * `/cargas/calcular-rota`, manda o corpo exato que o backend espera e
 * desembrulha o envelope (`data`) antes de devolver ao form. Se algum dia o
 * frontend mandar a API key do Google (ou esquecer de desembrulhar), os outros
 * testes deste projeto nao vao perceber — este, sim.
 */
describe('CargaService', () => {
  let service: CargaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CargaService);
    http = TestBed.inject(HttpTestingController);
  });

  it('calcularRota chama o endpoint e devolve a estimativa desembrulhada', () => {
    const dados = {
      origemCidade: 'Sao Paulo',
      origemUf: 'SP',
      origemEndereco: null,
      destinoCidade: 'Campinas',
      destinoUf: 'SP',
      destinoEndereco: null,
    };

    service.calcularRota(dados).subscribe((estimativa) => {
      expect(estimativa.distanciaKm).toBe(96);
      expect(estimativa.tempoEstimadoMinutos).toBe(85);
    });

    const requisicao = http.expectOne(`${environment.apiUrl}/cargas/calcular-rota`);
    expect(requisicao.request.method).toBe('POST');
    // So os enderecos vao ao backend — id nem tenant (vem do token).
    expect(requisicao.request.body).toEqual(dados);

    requisicao.flush({
      success: true,
      data: { distanciaKm: 96, tempoEstimadoMinutos: 85 },
    });
    http.verify();
  });

  it('repassa estimativa nula quando o Google nao acha rota (nao e erro)', () => {
    const dados = {
      origemCidade: 'Ilha Sem Saida',
      origemUf: null,
      origemEndereco: null,
      destinoCidade: 'Ilha Sem Chegada',
      destinoUf: null,
      destinoEndereco: null,
    };

    service.calcularRota(dados).subscribe((estimativa) => {
      expect(estimativa.distanciaKm).toBeNull();
      expect(estimativa.tempoEstimadoMinutos).toBeNull();
    });

    const requisicao = http.expectOne(`${environment.apiUrl}/cargas/calcular-rota`);
    requisicao.flush({ success: true, data: { distanciaKm: null, tempoEstimadoMinutos: null } });
    http.verify();
  });

  it('buscarCep chama /ceps/{cep} e devolve o endereco desembrulhado', () => {
    service.buscarCep('01001000').subscribe((dados) => {
      expect(dados?.cidade).toBe('Sao Paulo');
      expect(dados?.uf).toBe('SP');
    });

    const requisicao = http.expectOne(`${environment.apiUrl}/ceps/01001000`);
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({
      success: true,
      data: {
        cep: '01001000',
        logradouro: 'Praca da Se',
        bairro: 'Se',
        cidade: 'Sao Paulo',
        uf: 'SP',
      },
    });
    http.verify();
  });

  it('repassa null quando o CEP nao existe (nao e erro)', () => {
    service.buscarCep('99999999').subscribe((dados) => {
      expect(dados).toBeNull();
    });

    const requisicao = http.expectOne(`${environment.apiUrl}/ceps/99999999`);
    requisicao.flush({ success: true, data: null });
    http.verify();
  });
});
