import { DecimalHoursPipe, DurationPipe } from './duration';

describe('shared/pipes/duration', () => {
  it('RG-01-08 : la durée d’une saisie s’affiche en heures et minutes', () => {
    const pipe = new DurationPipe();
    expect(pipe.transform(90)).toBe('1 h 30');
    expect(pipe.transform(420)).toBe('7 h 00');
    expect(pipe.transform(15)).toBe('0 h 15');
    expect(pipe.transform(4200)).toBe('70 h 00');
    expect(pipe.transform(0)).toBe('0 h 00');
  });

  it('CA-01-09 : 90 min et 8 h font un total de 9,5 h', () => {
    expect(new DecimalHoursPipe().transform(90 + 480)).toBe('9,5 h');
  });

  it('RG-01-13 : le total s’affiche en heures décimales, à la virgule', () => {
    const pipe = new DecimalHoursPipe();
    expect(pipe.transform(0)).toBe('0 h');
    expect(pipe.transform(420)).toBe('7 h');
    expect(pipe.transform(15)).toBe('0,25 h');
    expect(pipe.transform(4200)).toBe('70 h');
    expect(pipe.transform(60000)).toBe('1000 h');
  });
});
