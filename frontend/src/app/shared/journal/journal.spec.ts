import { TestBed } from '@angular/core/testing';
import {
  Granularity,
  TagColor,
  TimeEntryResponse,
  WeeklyTimeEntriesResponse,
} from '../../core/api/generated';
import { TimeEntryCard } from './time-entry-card';
import { WeekJournal } from './week-journal';
import { WeekNavigator } from './week-navigator';

const hour = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse => ({
  id: 1,
  granularity: Granularity.Hour,
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true },
  durationMinutes: 90,
  version: 0,
  ...over,
});
const day = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse =>
  hour({
    id: 2,
    granularity: Granularity.Day,
    startDate: '2026-10-06',
    startTime: null,
    endTime: null,
    activityType: { code: 'TRAVAIL_PROJET', label: 'Travail projet', color: TagColor.Info, active: true },
    project: { code: 'APOLLO', name: 'Apollo', active: true },
    durationMinutes: 420,
    ...over,
  });
const range = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse =>
  hour({
    id: 3,
    granularity: Granularity.Range,
    startDate: '2026-10-08',
    endDate: '2026-10-13',
    startTime: null,
    endTime: null,
    activityType: { code: 'FORMATION', label: 'Formation', color: TagColor.Success, active: true },
    durationMinutes: 1680,
    ...over,
  });
const week = (entries: TimeEntryResponse[], totalMinutes: number): WeeklyTimeEntriesResponse => ({
  weekStart: '2026-10-05',
  weekEnd: '2026-10-11',
  totalMinutes,
  entries,
});

describe('TimeEntryCard', () => {
  async function render(entry: TimeEntryResponse, editable = true) {
    const fixture = TestBed.createComponent(TimeEntryCard);
    fixture.componentRef.setInput('entry', entry);
    fixture.componentRef.setInput('editable', editable);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    return { fixture, cy: (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`) };
  }

  it('CA-01-01 : affiche le type en tag coloré, le créneau et la durée de 90 min', async () => {
    const { cy } = await render(hour());
    expect(cy('activity-type-tag')?.textContent).toContain('Réunion');
    expect(cy('saisie-1')?.querySelector('.p-tag-warn')).not.toBeNull();
    expect(cy('saisie-periode')?.textContent).toContain('09:00 – 10:30');
    expect(cy('saisie-duree')?.textContent).toContain('1 h 30');
    expect(cy('saisie-projet')).toBeNull();
  });

  it('affiche le projet et « Journée entière » pour une JOURNEE', async () => {
    const { cy } = await render(day());
    expect(cy('saisie-projet')?.textContent).toContain('Apollo');
    expect(cy('saisie-periode')?.textContent).toContain('Journée entière');
    expect(cy('saisie-duree')?.textContent).toContain('7 h 00');
  });

  it('affiche les bornes et la durée enregistrée d’une PLAGE', async () => {
    const { cy } = await render(range());
    expect(cy('saisie-periode')?.textContent).toContain('Du 08/10 au 13/10');
    expect(cy('saisie-duree')?.textContent).toContain('28 h 00');
  });

  it('CA-02-07 : une saisie dont le type et le projet sont désactivés garde libellé, couleur et projet', async () => {
    const { cy } = await render(
      day({
        activityType: { code: 'REVUE_MR', label: 'Revue de MR', color: TagColor.Contrast, active: false },
        project: { code: 'APOLLO', name: 'Apollo', active: false },
      }),
    );
    expect(cy('activity-type-tag')?.textContent).toContain('Revue de MR');
    expect(cy('saisie-2')?.querySelector('.p-tag-contrast')).not.toBeNull();
    expect(cy('saisie-projet')?.textContent).toContain('Apollo');
  });

  it('RG-01-10 : « Modifier » et « Supprimer » nomment la saisie et émettent la saisie', async () => {
    const { fixture, cy } = await render(hour());
    const edited = vi.fn();
    const removed = vi.fn();
    fixture.componentInstance.edit.subscribe(edited);
    fixture.componentInstance.remove.subscribe(removed);
    const editButton = cy('btn-modifier-saisie-1')?.querySelector('button');
    const removeButton = cy('btn-supprimer-saisie-1')?.querySelector('button');
    expect(editButton?.getAttribute('aria-label')).toBe('Modifier la saisie Réunion du 05/10');
    expect(removeButton?.getAttribute('aria-label')).toBe('Supprimer la saisie Réunion du 05/10');
    editButton?.click();
    removeButton?.click();
    expect(edited).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(removed).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  });

  it('en lecture seule, aucun bouton de modification ni de suppression', async () => {
    const { cy } = await render(hour(), false);
    expect(cy('btn-modifier-saisie-1')).toBeNull();
    expect(cy('btn-supprimer-saisie-1')).toBeNull();
  });

  it('SFD-04 E2 : le commentaire n’est affiché que si showComment est vrai (faux par défaut)', async () => {
    const entry = hour({ comment: 'Revue du lot 2' });
    const { fixture, cy } = await render(entry);
    expect(cy('saisie-commentaire')).toBeNull();
    fixture.componentRef.setInput('showComment', true);
    await fixture.whenStable();
    expect(cy('saisie-commentaire')?.textContent).toContain('Revue du lot 2');
  });

  it('SFD-04 E2 : showComment n’affiche rien pour une saisie sans commentaire', async () => {
    const { fixture, cy } = await render(hour({ comment: null }));
    fixture.componentRef.setInput('showComment', true);
    await fixture.whenStable();
    expect(cy('saisie-commentaire')).toBeNull();
  });
});

describe('WeekJournal', () => {
  async function render(value: WeeklyTimeEntriesResponse, editable = true, today = '2026-10-14') {
    const fixture = TestBed.createComponent(WeekJournal);
    fixture.componentRef.setInput('week', value);
    fixture.componentRef.setInput('editable', editable);
    fixture.componentRef.setInput('today', today);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const entriesOf = (iso: string) =>
      Array.from(root.querySelectorAll(`[data-cy="jour-${iso}"] article`)).map((a) =>
        a.getAttribute('data-cy'),
      );
    return { fixture, root, entriesOf };
  }

  it('SFD-01 E1 : sept jours du lundi au dimanche, chaque saisie sur son jour', async () => {
    const { root, entriesOf } = await render(week([hour(), day()], 510));
    const days = Array.from(root.querySelectorAll('[data-cy^="jour-"]'));
    expect(days.map((d) => d.getAttribute('data-cy'))).toEqual([
      'jour-2026-10-05',
      'jour-2026-10-06',
      'jour-2026-10-07',
      'jour-2026-10-08',
      'jour-2026-10-09',
      'jour-2026-10-10',
      'jour-2026-10-11',
    ]);
    expect(days[0].querySelector('h2')?.textContent).toContain('lundi');
    expect(days[0].querySelector('h2')?.textContent).toContain('05/10');
    expect(days[6].querySelector('h2')?.textContent).toContain('dimanche');
    expect(entriesOf('2026-10-05')).toEqual(['saisie-1']);
    expect(entriesOf('2026-10-06')).toEqual(['saisie-2']);
    expect(entriesOf('2026-10-07')).toEqual([]);
    expect(root.querySelector('[data-cy="jour-2026-10-07"]')?.textContent).toContain('Aucune saisie');
  });

  it('CA-01-09 : des saisies de 90 min et 7 h donnent un total affiché de 8,5 h', async () => {
    const { root } = await render(week([hour(), day()], 510));
    expect(root.querySelector('[data-cy="total-semaine"]')?.textContent).toContain('8,5 h');
  });

  it('RG-01-13 : une PLAGE est affichée sur chaque jour couvert, week-end compris, et le total vient de l’API', async () => {
    // Plage du jeudi 08/10 au mardi 13/10 : 4 jours ouvrés au total, dont 2 dans la semaine affichée.
    const { root, entriesOf } = await render(week([range()], 840));
    expect(entriesOf('2026-10-07')).toEqual([]);
    for (const iso of ['2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11']) {
      expect(entriesOf(iso)).toEqual(['saisie-3']);
    }
    // Le front n'additionne pas la durée enregistrée de la plage (28 h) : il affiche le total de l'API (14 h).
    expect(root.querySelector('[data-cy="total-semaine"]')?.textContent).toContain('14 h');
  });

  it('relaie « Modifier » et « Supprimer » d’une saisie', async () => {
    const { fixture, root } = await render(week([hour()], 90));
    const edited = vi.fn();
    const removed = vi.fn();
    fixture.componentInstance.edit.subscribe(edited);
    fixture.componentInstance.remove.subscribe(removed);
    root.querySelector<HTMLElement>('[data-cy="btn-modifier-saisie-1"] button')?.click();
    root.querySelector<HTMLElement>('[data-cy="btn-supprimer-saisie-1"] button')?.click();
    expect(edited).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
    expect(removed).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  });

  it('en lecture seule, ni les saisies ni les jours n’ont de bouton', async () => {
    const { root } = await render(week([hour()], 90), false);
    expect(root.querySelector('[data-cy="saisie-1"]')).not.toBeNull();
    expect(root.querySelector('button')).toBeNull();
  });

  it('SFD-04 E2 : le commentaire des saisies est relayé aux cartes quand showComment est vrai, sinon absent', async () => {
    const { fixture, root } = await render(week([hour({ comment: 'Point hebdo' })], 90), false);
    expect(root.querySelector('[data-cy="saisie-commentaire"]')).toBeNull();
    fixture.componentRef.setInput('showComment', true);
    await fixture.whenStable();
    expect(root.querySelector('[data-cy="saisie-commentaire"]')?.textContent).toContain('Point hebdo');
  });

  it('SFD-04 E2 : en lecture seule, aucun « + » de jour, même sur un jour passé', async () => {
    const { root } = await render(week([], 0), false, '2026-10-14');
    expect(root.querySelector('[data-cy^="btn-ajouter-saisie-"]')).toBeNull();
  });

  it('SFD-01 E1 : chaque jour porte un « + » nommé, qui émet la date de ce jour', async () => {
    const { fixture, root } = await render(week([hour()], 90));
    const added = vi.fn();
    fixture.componentInstance.add.subscribe(added);
    expect(root.querySelectorAll('[data-cy^="btn-ajouter-saisie-"]')).toHaveLength(7);
    const button = root.querySelector<HTMLElement>(
      '[data-cy="btn-ajouter-saisie-2026-10-07"] button',
    );
    expect(button?.getAttribute('aria-label')).toBe('Ajouter une saisie le mercredi 07/10');
    button?.click();
    expect(added).toHaveBeenCalledWith('2026-10-07');
  });

  it('RG-01-07 : pas de « + » sur un jour futur ; le jour même en porte un', async () => {
    const { root } = await render(week([], 0), true, '2026-10-07');
    const days = Array.from(root.querySelectorAll('[data-cy^="btn-ajouter-saisie-"]')).map((b) =>
      b.getAttribute('data-cy'),
    );
    expect(days).toEqual([
      'btn-ajouter-saisie-2026-10-05',
      'btn-ajouter-saisie-2026-10-06',
      'btn-ajouter-saisie-2026-10-07',
    ]);
  });
});

describe('WeekNavigator', () => {
  async function render() {
    const fixture = TestBed.createComponent(WeekNavigator);
    fixture.componentRef.setInput('weekStart', '2026-10-05');
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const click = async (name: string) => {
      root.querySelector<HTMLElement>(`[data-cy="${name}"] button`)?.click();
      await fixture.whenStable();
    };
    const label = () => root.querySelector('[data-cy="libelle-semaine"]')?.textContent;
    return { fixture, click, label };
  }

  it('SFD-01 E1 : affiche la semaine du lundi au dimanche', async () => {
    const { label } = await render();
    expect(label()).toContain('Semaine du 05/10/2026 au 11/10/2026');
  });

  it('SFD-01 E1 : « Semaine précédente » et « Semaine suivante » décalent de sept jours', async () => {
    const { fixture, click, label } = await render();
    await click('btn-semaine-precedente');
    expect(fixture.componentInstance.weekStart()).toBe('2026-09-28');
    expect(label()).toContain('Semaine du 28/09/2026 au 04/10/2026');
    await click('btn-semaine-suivante');
    await click('btn-semaine-suivante');
    expect(fixture.componentInstance.weekStart()).toBe('2026-10-12');
  });
});
