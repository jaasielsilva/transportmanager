import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api.model';
import { CargaResumo } from '../../carga/models/carga.model';
import { MotoristaDetalhe } from '../../motorista/models/motorista.model';
import { PosicaoAtual } from '../models/rastreamento.model';

/**
 * Molde do kit — HttpClient so aqui; componente nunca chama a API direto.
 * Feature nova ("Minhas entregas"): reusa endpoints de motorista/carga que ja
 * existem, e so tem endpoint proprio para as posicoes de GPS.
 */
@Injectable({ providedIn: 'root' })
export class RastreamentoService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Motorista vinculado ao usuario logado. null se o usuario nao e motorista de ninguem. */
  buscarMeuMotorista(): Observable<MotoristaDetalhe | null> {
    return this.http.get<ApiResponse<MotoristaDetalhe>>(`${this.api}/motoristas/me`).pipe(
      map((r) => r.data),
      catchError(() => of(null)),
    );
  }

  /** Cargas em transito do motorista — reusa o filtro opcional do `listar` de cargas. */
  listarMinhasCargasAtivas(motoristaId: number): Observable<CargaResumo[]> {
    return this.http
      .get<ApiResponse<PageResponse<CargaResumo>>>(`${this.api}/cargas`, {
        params: { motoristaId, status: 'EM_TRANSITO', size: 50 },
      })
      .pipe(map((r) => r.data.content));
  }

  /** POST do motorista a cada ~12s enquanto o rastreamento estiver ativo. */
  enviarPosicao(cargaId: number, lat: number, lng: number): Observable<unknown> {
    return this.http.post(`${this.api}/cargas/${cargaId}/posicoes`, { latitude: lat, longitude: lng });
  }

  /** GET de quem acompanha a cada ~8s. */
  posicaoAtual(cargaId: number): Observable<PosicaoAtual | null> {
    return this.http.get<ApiResponse<PosicaoAtual>>(`${this.api}/cargas/${cargaId}/posicoes/atual`).pipe(
      map((r) => r.data),
      catchError(() => of(null)),
    );
  }
}
