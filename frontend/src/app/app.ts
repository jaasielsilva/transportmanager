import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmacaoComponent } from './shared/confirmacao/confirmacao.component';
import { ToastComponent } from './shared/toast/toast.component';

/**
 * Raiz da aplicacao. So o outlet e os dois componentes globais.
 *
 * Toast e confirmacao ficam AQUI, montados uma vez, porque sao usados de
 * qualquer lugar (inclusive de dentro de um interceptor, que nao tem
 * componente). Instanciar em cada tela daria varias pilhas de toast
 * competindo pelo mesmo canto.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastComponent, ConfirmacaoComponent],
  template: `
    <router-outlet />
    <app-toasts />
    <app-confirmacao />
  `,
})
export class App {}
