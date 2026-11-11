import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { I18n } from './i18n.service';

/** RG-08-06: every request to the API carries the interface language, and the server answers in that language. */
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  if (!/^(https?:\/\/[^/]+)?\/api\//.test(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'Accept-Language': inject(I18n).lang() } }));
};
