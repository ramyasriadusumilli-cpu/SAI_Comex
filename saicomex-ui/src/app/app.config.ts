import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor, errorInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    // withComponentInputBinding lets a route param arrive as a component
    // `input()`, which keeps detail components free of ActivatedRoute plumbing.
    provideRouter(routes, withComponentInputBinding()),
    // Order matters: authInterceptor must attach the token before
    // errorInterceptor can interpret a 401 as an expired session.
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
  ],
};
