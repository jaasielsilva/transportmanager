import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Molde do kit — features/billing/assinatura.page.ts
 *
 * TELA DE REGULARIZACAO — o destino do 402.
 *
 * Ela existe porque cobranca que falha e, quase sempre, cartao expirado — e o
 * cliente nao sabe. Bloquear sem explicar faz ele achar que o produto quebrou;
 * nao explicar o que ainda funciona faz ele achar que perdeu os dados.
 *
 * Tres regras de tom, todas deliberadas:
 *   1. dizer o que aconteceu sem acusar ninguem
 *   2. dizer o que CONTINUA funcionando (os dados estao aqui)
 *   3. um unico botao, o de resolver
 *
 * Esta rota NUNCA fica atras do bloqueio de assinatura — cliente que nao
 * consegue abrir a tela de pagamento nao consegue pagar.
 */
@Component({
  selector: 'app-assinatura',
  imports: [RouterLink],
  template: `
    <div class="card" style="max-width: 640px; margin: 40px auto">
      <h1>{{ titulo() }}</h1>
      <p>{{ explicacao() }}</p>

      <p class="texto-suave">
        Seus dados continuam aqui, completos. Assim que o pagamento for confirmado, o acesso volta
        sozinho — sem precisar falar com ninguem.
      </p>

      <div style="display: flex; gap: 8px; margin-top: 20px; flex-wrap: wrap">
        <!--
          TODO por projeto: apontar para o portal do gateway (Stripe/Asaas/
          Mercado Pago) assim que o GatewayBilling estiver implementado.
          Ate la, o caminho honesto e o contato — melhor que um botao que nao
          faz nada.
        -->
        <a class="btn" href="mailto:silvajasiel30@gmail.com?subject=Regularizar assinatura">
          Regularizar pagamento
        </a>
        <a class="btn btn-secundario" routerLink="/inicio">Voltar ao sistema</a>
      </div>

      <p class="texto-suave" style="margin-top: 24px; margin-bottom: 0">
        Situacao atual: <strong>{{ usuario()?.assinaturaStatus }}</strong> ·
        acesso <strong>{{ usuario()?.nivelAcesso }}</strong>
      </p>
    </div>
  `,
})
export class AssinaturaPage {
  private readonly auth = inject(AuthService);

  protected readonly usuario = this.auth.usuario;

  protected readonly titulo = computed(() =>
    this.auth.nivelAcesso() === 'SUSPENSO'
      ? 'Seu acesso esta pausado'
      : 'Nao conseguimos confirmar seu pagamento',
  );

  protected readonly explicacao = computed(() => {
    switch (this.auth.nivelAcesso()) {
      case 'SUSPENSO':
        return 'A cobranca segue em aberto ha algum tempo e o acesso ao sistema foi pausado. Regularizando, tudo volta exatamente como estava.';
      case 'SOMENTE_LEITURA':
        return 'O pagamento nao foi confirmado. Por enquanto voce consegue consultar tudo, mas nao registrar alteracoes.';
      default:
        return 'A ultima cobranca nao foi confirmada. Normalmente e cartao vencido ou limite. O sistema continua liberado por enquanto.';
    }
  });
}
