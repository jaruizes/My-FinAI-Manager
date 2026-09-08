// The browser always talks to its own origin; the container's reverse proxy
// (nginx) forwards /api to the backend, and `ng serve` proxies it in local dev.
export const environment = {
  apiBaseUrl: '/api',
};
