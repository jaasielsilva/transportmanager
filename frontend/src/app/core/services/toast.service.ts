import { Injectable, signal } from '@angular/core';

export type TipoDeToast = 'sucesso' | 'erro' | 'aviso';

export interface Toast {
  id: number;
  tipo: TipoDeToast;
  texto: string;
}

/**
 * Feedback de tela. Chamado principalmente pelo errorInterceptor — por isso
 * qualquer falha da API ja aparece para o usuario sem o componente fazer nada.
 *
 * Erro tem duracao maior que sucesso de proposito: mensagem de erro que some
 * antes de ser lida e o mesmo que nao ter mensagem.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private proximoId = 1;

  readonly toasts = signal<Toast[]>([]);

  sucesso(texto: string): void {
    this.mostrar('sucesso', texto, 3500);
  }

  erro(texto: string): void {
    this.mostrar('erro', texto, 7000);
  }

  aviso(texto: string): void {
    this.mostrar('aviso', texto, 5000);
  }

  fechar(id: number): void {
    this.toasts.update((lista) => lista.filter((t) => t.id !== id));
  }

  private mostrar(tipo: TipoDeToast, texto: string, duracaoMs: number): void {
    const id = this.proximoId++;
    this.toasts.update((lista) => [...lista, { id, tipo, texto }]);
    setTimeout(() => this.fechar(id), duracaoMs);
  }
}
