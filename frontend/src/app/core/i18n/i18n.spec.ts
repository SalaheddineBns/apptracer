import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PrimeNG } from 'primeng/config';
import { formatWeekdayName } from '../../shared/dates/week';
import { DICTIONARIES, I18n } from './i18n.service';
import { languageInterceptor } from './language.interceptor';
import { detectLang } from './lang';
import { TPipe } from './t.pipe';

function leafPaths(node: object, prefix = ''): string[] {
  return Object.entries(node).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : leafPaths(value as object, `${prefix}${key}.`),
  );
}

function browser(language: string): void {
  Object.defineProperty(window.navigator, 'language', { value: language, configurable: true });
  Object.defineProperty(window.navigator, 'languages', { value: [language], configurable: true });
}

describe('core/i18n', () => {
  beforeEach(() => {
    localStorage.clear();
    browser('fr-FR');
  });
  afterEach(() => browser('fr-FR'));

  it.each([
    ['fr-FR', 'fr'],
    ['fr', 'fr'],
    ['fr-CA', 'fr'],
    ['en-US', 'en'],
    ['de-DE', 'en'],
    ['', 'en'],
  ])('CA-08-01 : un premier accès avec un navigateur « %s » est en %s', (language, expected) => {
    expect(detectLang(language)).toBe(expected);
    browser(language);
    expect(TestBed.inject(I18n).lang()).toBe(expected);
  });

  it('CA-08-01 : le français est aussi détecté quand le navigateur en annonce plusieurs et que la préférée est le français', () => {
    expect(detectLang('fr-FR')).toBe('fr');
    expect(detectLang(undefined)).toBe('en');
  });

  it('CA-08-04, RG-08-03 : le choix est conservé dans localStorage et prime sur la langue du navigateur', () => {
    browser('fr-FR');
    TestBed.inject(I18n).setLang('en');
    expect(localStorage.getItem('lang')).toBe('en');

    TestBed.resetTestingModule();
    expect(TestBed.inject(I18n).lang()).toBe('en');

    localStorage.setItem('lang', 'invalide');
    TestBed.resetTestingModule();
    expect(TestBed.inject(I18n).lang()).toBe('fr');
  });

  it('RG-08-05 : le changement de langue met à jour la langue du document, le titre et PrimeNG', () => {
    const i18n = TestBed.inject(I18n);
    expect(document.documentElement.lang).toBe('fr');
    expect(TestBed.inject(PrimeNG).translation.today).toBe('Aujourd’hui');

    i18n.setLang('en');
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toBe(DICTIONARIES.en.app.title);
    const primeng = TestBed.inject(PrimeNG).translation;
    expect(primeng.today).toBe('Today');
    expect(primeng.dayNames?.[1]).toBe('Monday');
    expect(primeng.firstDayOfWeek).toBe(1);
    expect(primeng.dateFormat).toBe('dd/mm/yy');

    i18n.setLang('fr');
    expect(document.documentElement.lang).toBe('fr');
    expect(TestBed.inject(PrimeNG).translation.dayNames?.[1]).toBe('lundi');
  });

  it('CA-08-08 : les dictionnaires fr et en ont exactement le même ensemble de clés', () => {
    const fr = leafPaths(DICTIONARIES.fr).sort();
    expect(fr.length).toBeGreaterThan(0);
    expect(leafPaths(DICTIONARIES.en).sort()).toEqual(fr);
  });

  it('RG-08-08 : les noms des jours suivent la langue', () => {
    expect(formatWeekdayName('2026-10-05', 'fr')).toBe('lundi');
    expect(formatWeekdayName('2026-10-05', 'en')).toBe('Monday');
    expect(formatWeekdayName('2026-10-11', 'en')).toBe('Sunday');
  });

  it('traduit une clé avec paramètres', () => {
    const i18n = TestBed.inject(I18n);
    expect(i18n.t('language.fr')).toBe('Français');
    expect(i18n.t('language.label')).toBe('Langue de l’interface');
    i18n.setLang('en');
    expect(i18n.t('language.label')).toBe('Interface language');
  });

  it('le pipe t suit le changement de langue sans recréer le composant', async () => {
    @Component({ imports: [TPipe], template: `<p data-cy="texte">{{ 'language.label' | t }}</p>` })
    class Host {}
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const text = () => (fixture.nativeElement as HTMLElement).textContent;
    expect(text()).toBe('Langue de l’interface');
    TestBed.inject(I18n).setLang('en');
    fixture.detectChanges();
    expect(text()).toBe('Interface language');
  });

  describe('intercepteur Accept-Language', () => {
    let http: HttpClient;
    let controller: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideHttpClient(withInterceptors([languageInterceptor])), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpClient);
      controller = TestBed.inject(HttpTestingController);
    });

    it('CA-08-05 : chaque requête /api porte la langue courante, aussi après un changement', () => {
      http.get('/api/v1/time-entries').subscribe();
      expect(controller.expectOne('/api/v1/time-entries').request.headers.get('Accept-Language')).toBe('fr');

      TestBed.inject(I18n).setLang('en');
      http.get('/api/v1/auth/login').subscribe();
      expect(controller.expectOne('/api/v1/auth/login').request.headers.get('Accept-Language')).toBe('en');
    });

    it('ne touche pas aux requêtes hors /api', () => {
      http.get('/assets/x.json').subscribe();
      expect(controller.expectOne('/assets/x.json').request.headers.has('Accept-Language')).toBe(false);
    });
  });
});
