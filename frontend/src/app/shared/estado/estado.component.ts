import { Component, input, output } from '@angular/core';

/**
 * Molde do kit — shared/estado/estado.component.ts
 *
 * Os tres estados que TODA listagem tem e quase todo projeto esquece:
 * carregando, vazio e erro (skill, secao 5).
 *
 * O estado VAZIO e o que mais vale dinheiro: e a primeira tela que o cliente
 * novo enxerga. Tela vazia sem explicacao e sem botao e a principal razao de
 * um trial nao virar assinatura — a pessoa entra, nao sabe o que fazer e nao
 * volta. Por isso o vazio aqui tem titulo, explicacao e um CTA.
 */
@Component({
  selector: 'app-estado',
  template: `
    @if (carregando()) {
      <div class="caixa">
        <div class="spinner" aria-label="Carregando"></div>
        <p class="texto-suave">Carregando...</p>
      </div>
    } @else if (erro()) {
      <div class="caixa">
        <p class="titulo">Nao foi possivel carregar</p>
        <p class="texto-suave">{{ erro() }}</p>
        <button type="button" class="btn btn-secundario" (click)="acao.emit()">
          Tentar de novo
        </button>
      </div>
    } @else if (vazio()) {
      <div class="caixa">
        <p class="titulo">{{ tituloVazio() }}</p>
        <p class="texto-suave">{{ textoVazio() }}</p>
        @if (ctaVazio()) {
          <button type="button" class="btn" (click)="acao.emit()">{{ ctaVazio() }}</button>
        }
      </div>
    }
  `,
  styles: [
    `
      .caixa {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
        padding: 48px 24px;
        text-align: center;
      }
      .titulo {
        margin: 0;
        font-weight: 600;
      }
      .spinner {
        width: 28px;
        height: 28px;
        border: 3px solid var(--borda);
        border-top-color: var(--color-primary);
        border-radius: 50%;
        animation: girar 0.8s linear infinite;
      }
      @keyframes girar {
        to {
          transform: rotate(360deg);
        }
      }
      /* Respeita quem configurou o sistema para reduzir animacao. */
      @media (prefers-reduced-motion: reduce) {
        .spinner {
          animation-duration: 2s;
        }
      }
    `,
  ],
})
export class EstadoComponent {
  readonly carregando = input(false);
  readonly vazio = input(false);
  readonly erro = input<string | null>(null);

  readonly tituloVazio = input('Nada por aqui ainda');
  readonly textoVazio = input('Cadastre o primeiro registro para comecar.');
  readonly ctaVazio = input<string | null>(null);

  /** Clique no CTA do vazio ou no "tentar de novo" do erro. */
  readonly acao = output<void>();
}
