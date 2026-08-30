import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api.model';
import {
  CargaAtualizarStatus,
  CargaCalcularRota,
  CargaDetalhe,
  CargaEstimativaRota,
  CargaMapaRota,
  CargaResumo,
  CargaSalvar,
  CepDados,
} from '../models/carga.model';

/**
 * Molde do kit — features/carga/services/carga.service.ts
 *
 * SERVICE DE REFERENCIA. Todo acesso HTTP do projeto se parece com este:
 *   - HttpClient SO aqui; componente nunca chama a API direto
 *   - a URL vem de environment, nunca escrita no meio do codigo
 *   - o envelope ApiResponse e desembrulhado aqui (`map(r => r.data)`), para
 *     que nenhum componente precise conhecer o formato do envelope
 *   - nada de tratar erro: o errorInterceptor cuida do generico e o componente
 *     trata so o que for regra de negocio dele
 */
@Injectable({ providedIn: 'root' })
export class CargaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/cargas`;

  listar(
    q = '',
    page = 0,
    size = 20,
    filtros?: { motoristaId?: number; status?: string },
  ): Observable<PageResponse<CargaResumo>> {
    let params = new HttpParams().set('q', q).set('page', page).set('size', size).set('sort', 'nome,asc');
    if (filtros?.motoristaId != null) {
      params = params.set('motoristaId', filtros.motoristaId);
    }
    if (filtros?.status) {
      params = params.set('status', filtros.status);
    }

    return this.http
      .get<ApiResponse<PageResponse<CargaResumo>>>(this.url, { params })
      .pipe(map((r) => r.data));
  }

  buscar(id: number): Observable<CargaDetalhe> {
    return this.http
      .get<ApiResponse<CargaDetalhe>>(`${this.url}/${id}`)
      .pipe(map((r) => r.data));
  }

  criar(dados: CargaSalvar): Observable<CargaDetalhe> {
    return this.http
      .post<ApiResponse<CargaDetalhe>>(this.url, dados)
      .pipe(map((r) => r.data));
  }

  atualizar(id: number, dados: CargaSalvar): Observable<CargaDetalhe> {
    return this.http
      .put<ApiResponse<CargaDetalhe>>(`${this.url}/${id}`, dados)
      .pipe(map((r) => r.data));
  }

  /**
   * Atualiza o status da carga seguindo o fluxo de transporte
   * Endpoint específico para mudanças de status
   */
  atualizarStatus(id: number, dados: CargaAtualizarStatus): Observable<CargaDetalhe> {
    return this.http
      .patch<ApiResponse<CargaDetalhe>>(`${this.url}/${id}/status`, dados)
      .pipe(map((r) => r.data));
  }

  /** Soft delete no backend — o registro continua lá, marcado. */
  excluir(id: number): Observable<unknown> {
    return this.http.delete(`${this.url}/${id}`);
  }

  /**
   * Estimativa de rota (Distance Matrix). Endpoint helper do form: o backend
   * conversa com o Google (a key nunca sai do servidor) e so devolve numeros —
   * quem grava e o "Salvar" normal. Null na resposta nao e erro: e rota nao
   * encontrada.
   */
  calcularRota(dados: CargaCalcularRota): Observable<CargaEstimativaRota> {
    return this.http
      .post<ApiResponse<CargaEstimativaRota>>(`${this.url}/calcular-rota`, dados)
      .pipe(map((r) => r.data));
  }

  /**
   * Autofill do form: CEP -> endereco/cidade/UF. O backend conversa com o
   * ViaCEP (gratuito, sem chave) e devolve null para CEP inexistente — nao e
   * erro, o form avisa e deixa digitar na mao.
   */
  buscarCep(cep: string): Observable<CepDados | null> {
    return this.http
      .get<ApiResponse<CepDados | null>>(`${environment.apiUrl}/ceps/${cep}`)
      .pipe(map((r) => r.data));
  }

  /**
   * Mapa da rota da carga ja salva (origem, destino, geometria) para o
   * componente Leaflet embutido no formulario de detalhe.
   */
  rotaMapa(id: number): Observable<CargaMapaRota> {
    return this.http
      .get<ApiResponse<CargaMapaRota>>(`${this.url}/${id}/rota-mapa`)
      .pipe(map((r) => r.data));
  }
}
