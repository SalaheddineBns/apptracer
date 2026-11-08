import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { I18n } from './i18n.service';

/** RG-08-06 : chaque requête vers l'API porte la langue de l'interface, le serveur répond dans cette langue. */
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  if (!/^(https?:\/\/[^/]+)?\/api\//.test(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'Accept-Language': inject(I18n).lang() } }));
};
