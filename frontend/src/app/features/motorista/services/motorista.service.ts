import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api.model';
import { MotoristaDetalhe, MotoristaResumo, MotoristaSalvar } from '../models/motorista.model';

/**
 * Molde do kit — features/carga/services/carga.service.ts. HttpClient so
 * aqui; componente nunca chama a API direto.
 */
@Injectable({ providedIn: 'root' })
export class MotoristaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/motoristas`;

  listar(q = '', page = 0, size = 20): Observable<PageResponse<MotoristaResumo>> {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size).set('sort', 'nome,asc');
    return this.http
      .get<ApiResponse<PageResponse<MotoristaResumo>>>(this.url, { params })
      .pipe(map((r) => r.data));
  }

  buscar(id: number): Observable<MotoristaDetalhe> {
    return this.http.get<ApiResponse<MotoristaDetalhe>>(`${this.url}/${id}`).pipe(map((r) => r.data));
  }

  /** Motorista vinculado ao usuario logado. 404 se o usuario nao e motorista de ninguem. */
  meuCadastro(): Observable<MotoristaDetalhe> {
    return this.http.get<ApiResponse<MotoristaDetalhe>>(`${this.url}/me`).pipe(map((r) => r.data));
  }

  criar(dados: MotoristaSalvar): Observable<MotoristaDetalhe> {
    return this.http.post<ApiResponse<MotoristaDetalhe>>(this.url, dados).pipe(map((r) => r.data));
  }

  atualizar(id: number, dados: MotoristaSalvar): Observable<MotoristaDetalhe> {
    return this.http.put<ApiResponse<MotoristaDetalhe>>(`${this.url}/${id}`, dados).pipe(map((r) => r.data));
  }

  excluir(id: number): Observable<unknown> {
    return this.http.delete(`${this.url}/${id}`);
  }
}
