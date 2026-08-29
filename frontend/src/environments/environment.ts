/**
 * Ambiente de desenvolvimento (`ng serve`).
 *
 * A URL da API NUNCA aparece hardcoded em service nenhum: em producao o
 * frontend e servido pelo mesmo Nginx que faz proxy de /api para o backend,
 * entao la a base e relativa. Trocar isso na mao em cada deploy e como se
 * perde uma sexta-feira.
 */
export const environment = {
  producao: false,
  apiUrl: 'http://localhost:8080/api/v1',
};
