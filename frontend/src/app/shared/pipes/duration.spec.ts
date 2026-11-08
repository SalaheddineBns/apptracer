import { TestBed } from '@angular/core/testing';
import { I18n } from '../../core/i18n/i18n.service';
import { DecimalHoursPipe, DurationPipe, formatDecimalHours } from './duration';

describe('shared/pipes/duration', () => {
  beforeEach(() => localStorage.clear());

  const decimalHours = () => TestBed.runInInjectionContext(() => new DecimalHoursPipe());

  it('RG-01-08 : la durée d’une saisie s’affiche en heures et minutes', () => {
    const pipe = new DurationPipe();
    expect(pipe.transform(90)).toBe('1 h 30');
    expect(pipe.transform(420)).toBe('7 h 00');
    expect(pipe.transform(15)).toBe('0 h 15');
    expect(pipe.transform(4200)).toBe('70 h 00');
    expect(pipe.transform(0)).toBe('0 h 00');
  });

  it('CA-01-09 : 90 min et 8 h font un total de 9,5 h', () => {
    expect(decimalHours().transform(90 + 480)).toBe('9,5 h');
  });

  it('RG-01-13 : le total s’affiche en heures décimales, à la virgule', () => {
    const pipe = decimalHours();
    expect(pipe.transform(0)).toBe('0 h');
    expect(pipe.transform(420)).toBe('7 h');
    expect(pipe.transform(15)).toBe('0,25 h');
    expect(pipe.transform(4200)).toBe('70 h');
    expect(pipe.transform(60000)).toBe('1000 h');
  });

  it('CA-08-06 : le total est « 9,5 h » en français et « 9.5 h » en anglais, la durée d’une saisie reste « 1 h 30 »', () => {
    expect(formatDecimalHours(570, 'fr')).toBe('9,5 h');
    expect(formatDecimalHours(570, 'en')).toBe('9.5 h');
    expect(formatDecimalHours(15, 'en')).toBe('0.25 h');
    const pipe = decimalHours();
    TestBed.inject(I18n).setLang('en');
    expect(pipe.transform(570)).toBe('9.5 h');
    TestBed.inject(I18n).setLang('fr');
    expect(pipe.transform(570)).toBe('9,5 h');
    expect(new DurationPipe().transform(90)).toBe('1 h 30');
  });
});
