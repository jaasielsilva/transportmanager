/**
 * Producao e homologacao usam o MESMO arquivo: a base e relativa porque quem
 * responde /api e o proprio Nginx da imagem do frontend (frontend/nginx.conf),
 * que faz proxy para backend:8080 pela rede interna do compose.
 *
 * Consequencia pratica: nao existe URL de ambiente para errar no deploy, e o
 * backend nao precisa expor porta nenhuma.
 */
export const environment = {
  producao: true,
  apiUrl: '/api/v1',
};
