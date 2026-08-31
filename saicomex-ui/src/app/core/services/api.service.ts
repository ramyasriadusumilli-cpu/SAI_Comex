import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Thin HTTP wrapper.
 *
 * The base URL is the relative path `/api`, which works unchanged in every
 * environment: locally the dev server proxies it (proxy.conf.json), and in
 * production nginx proxies it to the API container. No environment file, and
 * no chance of a build shipping with a hard-coded host in it.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {

  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  get<T>(path: string, params?: Record<string, unknown>): Observable<T> {
    return this.http.get<T>(this.base + path, { params: toParams(params) });
  }

  post<T>(path: string, body?: unknown, params?: Record<string, unknown>): Observable<T> {
    return this.http.post<T>(this.base + path, body ?? {}, { params: toParams(params) });
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(this.base + path, body);
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http.patch<T>(this.base + path, body);
  }

  delete<T>(path: string, params?: Record<string, unknown>): Observable<T> {
    return this.http.delete<T>(this.base + path, { params: toParams(params) });
  }

  upload<T>(path: string, form: FormData): Observable<T> {
    return this.http.post<T>(this.base + path, form);
  }
}

/**
 * Drops null, undefined and empty-string values.
 *
 * Sending `status=` for an unselected filter is not the same as omitting it:
 * the API treats an empty string as a real value and returns nothing, which
 * looks to the operator like missing data rather than a filter bug.
 */
function toParams(source?: Record<string, unknown>): HttpParams {
  let params = new HttpParams();
  if (!source) return params;
  for (const [key, value] of Object.entries(source)) {
    if (value === null || value === undefined || value === '') continue;
    if (Array.isArray(value)) {
      for (const item of value) params = params.append(key, String(item));
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}
