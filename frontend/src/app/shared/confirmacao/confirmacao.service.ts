import { Injectable, signal } from '@angular/core';

export interface PedidoDeConfirmacao {
  titulo: string;
  texto: string;
  confirmarTexto: string;
  perigo: boolean;
}

/**
 * Molde do kit — shared/confirmacao/confirmacao.service.ts
 *
 * Confirmacao antes de acao destrutiva (skill, secao 5). Nao usa
 * window.confirm de proposito: o dialogo nativo nao aceita o estilo do
 * produto, escreve o dominio do site no titulo e alguns navegadores permitem
 * ao usuario suprimi-lo — dai a exclusao passa direto.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacaoService {
  readonly pedido = signal<PedidoDeConfirmacao | null>(null);

  private resolver?: (confirmado: boolean) => void;

  perguntar(
    titulo: string,
    texto: string,
    opcoes: { confirmarTexto?: string; perigo?: boolean } = {},
  ): Promise<boolean> {
    this.pedido.set({
      titulo,
      texto,
      confirmarTexto: opcoes.confirmarTexto ?? 'Confirmar',
      perigo: opcoes.perigo ?? false,
    });

    return new Promise<boolean>((resolve) => (this.resolver = resolve));
  }

  responder(confirmado: boolean): void {
    this.pedido.set(null);
    this.resolver?.(confirmado);
    this.resolver = undefined;
  }
}
