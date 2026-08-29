import { Component, inject } from '@angular/core';
import { ConfirmacaoService } from './confirmacao.service';

/** Dialogo de confirmacao. Montado uma vez na raiz, junto com os toasts. */
@Component({
  selector: 'app-confirmacao',
  template: `
    @if (servico.pedido(); as p) {
      <div class="fundo" (click)="servico.responder(false)">
        <div class="caixa" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
          <h2>{{ p.titulo }}</h2>
          <p class="texto-suave">{{ p.texto }}</p>
          <div class="acoes">
            <button type="button" class="btn btn-secundario" (click)="servico.responder(false)">
              Cancelar
            </button>
            <button
              type="button"
              class="btn"
              [class.btn-perigo]="p.perigo"
              (click)="servico.responder(true)"
            >
              {{ p.confirmarTexto }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .fundo {
        position: fixed;
        inset: 0;
        z-index: 900;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 16px;
        background: rgba(17, 24, 39, 0.45);
      }
      .caixa {
        width: min(420px, 100%);
        padding: 24px;
        border-radius: var(--raio);
        background: var(--card-bg);
        box-shadow: var(--sombra-alta);
      }
      .acoes {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 20px;
      }
    `,
  ],
})
export class ConfirmacaoComponent {
  protected readonly servico = inject(ConfirmacaoService);
}
