import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

/**
 * Molde do kit — shared/toast/toast.component.ts
 *
 * Fica montado uma unica vez, na raiz da aplicacao. Como o errorInterceptor
 * publica no ToastService, qualquer falha de API vira mensagem na tela sem o
 * componente da vez escrever uma linha para isso.
 */
@Component({
  selector: 'app-toasts',
  template: `
    <div class="pilha" role="status" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast-' + t.tipo">
          <span>{{ t.texto }}</span>
          <button type="button" class="fechar" (click)="toast.fechar(t.id)" aria-label="Fechar">
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .pilha {
        position: fixed;
        top: 16px;
        right: 16px;
        z-index: 1000;
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-width: min(380px, calc(100vw - 32px));
      }
      .toast {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 12px 14px;
        border-radius: var(--raio-pequeno);
        box-shadow: var(--sombra-alta);
        color: #fff;
        font-size: 0.9rem;
      }
      .toast-sucesso {
        background: var(--sucesso);
      }
      .toast-erro {
        background: var(--erro);
      }
      .toast-aviso {
        background: var(--aviso);
      }
      .fechar {
        background: none;
        border: none;
        color: inherit;
        font-size: 1.1rem;
        line-height: 1;
        cursor: pointer;
        opacity: 0.8;
      }
    `,
  ],
})
export class ToastComponent {
  protected readonly toast = inject(ToastService);
}
