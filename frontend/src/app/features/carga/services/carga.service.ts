import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api.model';
import { CargaAtualizarStatus, CargaDetalhe, CargaResumo, CargaSalvar } from '../models/carga.model';

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

  listar(q = '', page = 0, size = 20): Observable<PageResponse<CargaResumo>> {
    const params = new HttpParams()
      .set('q', q)
      .set('page', page)
      .set('size', size)
      .set('sort', 'nome,asc');

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
}
