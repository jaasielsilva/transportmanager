import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api.model';
import {
  Dashboard,
  DunningEvento,
  SituacaoDeTenant,
  TenantDetalhe,
  TenantResumo,
  WebhookFalha,
} from '../models/platform.model';

/**
 * Molde do kit — features/platform/services/platform.service.ts
 *
 * Toda chamada aqui exige PLATFORM_ADMIN no backend (`@PreAuthorize` na classe
 * do controller). O guard da rota só evita que a pessoa chegue numa tela que
 * responderia 403.
 */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/platform`;

  dashboard(): Observable<Dashboard> {
    return this.http.get<ApiResponse<Dashboard>>(`${this.url}/dashboard`).pipe(map((r) => r.data));
  }

  tenants(
    situacao: SituacaoDeTenant = '',
    q = '',
    page = 0,
    size = 20,
  ): Observable<PageResponse<TenantResumo>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (situacao) {
      params = params.set('situacao', situacao);
    }
    if (q) {
      params = params.set('q', q);
    }
    return this.http
      .get<ApiResponse<PageResponse<TenantResumo>>>(`${this.url}/tenants`, { params })
      .pipe(map((r) => r.data));
  }

  tenant(id: number): Observable<TenantDetalhe> {
    return this.http
      .get<ApiResponse<TenantDetalhe>>(`${this.url}/tenants/${id}`)
      .pipe(map((r) => r.data));
  }

  /** O que a régua de dunning fez neste tenant — o outro lado do job. */
  dunning(id: number): Observable<DunningEvento[]> {
    return this.http
      .get<ApiResponse<DunningEvento[]>>(`${this.url}/tenants/${id}/dunning`)
      .pipe(map((r) => r.data));
  }

  /** Prazo concedido na mão. Auditado — existe para não virar UPDATE em produção. */
  prorrogar(id: number, dias: number): Observable<unknown> {
    return this.http.post(`${this.url}/tenants/${id}/prorrogar`, null, {
      params: new HttpParams().set('dias', dias),
    });
  }

  webhooksComFalha(): Observable<WebhookFalha[]> {
    return this.http
      .get<ApiResponse<WebhookFalha[]>>(`${this.url}/webhooks/falhas`)
      .pipe(map((r) => r.data));
  }

  reprocessarWebhook(eventoId: string): Observable<unknown> {
    return this.http.post(`${this.url}/webhooks/${eventoId}/reprocessar`, null);
  }
}
