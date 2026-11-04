import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  Granularity,
  TagColor,
  TimeEntriesService,
  TimeEntryResponse,
  WeeklyTimeEntriesResponse,
} from '../../../core/api/generated';
import { TimeEntriesStore } from './time-entries.store';

function entry(id: number, durationMinutes: number): TimeEntryResponse {
  return {
    id,
    granularity: Granularity.Hour,
    startDate: '2026-10-05',
    startTime: '09:00',
    endTime: '10:30',
    activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true },
    durationMinutes,
    version: 0,
  };
}

function week(weekStart: string, entries: TimeEntryResponse[], totalMinutes: number) {
  return { weekStart, weekEnd: '2026-10-11', totalMinutes, days: [], entries } as WeeklyTimeEntriesResponse;
}

describe('TimeEntriesStore', () => {
  const api = {
    listMyTimeEntries: vi.fn(),
    createTimeEntry: vi.fn(),
    updateTimeEntry: vi.fn(),
    deleteTimeEntry: vi.fn(),
  };

  function setup(): TimeEntriesStore {
    TestBed.configureTestingModule({
      providers: [{ provide: TimeEntriesService, useValue: api }],
    });
    return TestBed.inject(TimeEntriesStore);
  }

  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    TestBed.resetTestingModule();
  });

  it('CA-01-09 : charge la semaine demandée et garde le total renvoyé par le back', () => {
    api.listMyTimeEntries.mockReturnValue(of(week('2026-10-05', [entry(1, 90), entry(2, 420)], 510)));
    const store = setup();
    store.load('2026-10-05');
    expect(api.listMyTimeEntries.mock.calls[0][0]).toBe('2026-10-05');
    expect(store.week()?.totalMinutes).toBe(510);
    expect(store.week()?.entries.map((e) => e.id)).toEqual([1, 2]);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('expose l’erreur sans toast et n’affiche aucune semaine', () => {
    api.listMyTimeEntries.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    const store = setup();
    store.load('2026-10-05');
    expect(store.error()).toBe('Panne serveur');
    expect(store.week()).toBeNull();
    expect(store.loading()).toBe(false);
  });

  it('changement de semaine : l’ancienne semaine disparaît, la réponse la plus récente l’emporte', () => {
    const first = new Subject<WeeklyTimeEntriesResponse>();
    const second = new Subject<WeeklyTimeEntriesResponse>();
    api.listMyTimeEntries.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const store = setup();
    store.load('2026-10-05');
    store.load('2026-10-12');
    expect(store.week()).toBeNull();
    second.next(week('2026-10-12', [], 0));
    first.next(week('2026-10-05', [entry(1, 90)], 90));
    expect(store.week()?.weekStart).toBe('2026-10-12');
    expect(store.weekStart()).toBe('2026-10-12');
  });

  it('CA-01-01 : après création, la semaine affichée est rechargée', () => {
    api.listMyTimeEntries
      .mockReturnValueOnce(of(week('2026-10-05', [], 0)))
      .mockReturnValueOnce(of(week('2026-10-05', [entry(1, 90)], 90)));
    api.createTimeEntry.mockReturnValue(of(entry(1, 90)));
    const store = setup();
    store.load('2026-10-05');
    store
      .create({
        granularity: Granularity.Hour,
        startDate: '2026-10-05',
        startTime: '09:00',
        endTime: '10:30',
        activityTypeCode: 'REUNION',
      })
      .subscribe();
    expect(store.week()?.entries).toHaveLength(1);
    expect(store.week()?.totalMinutes).toBe(90);
  });

  it('RG-01-12 : après modification, la semaine affichée est rechargée', () => {
    api.listMyTimeEntries
      .mockReturnValueOnce(of(week('2026-10-05', [entry(1, 90)], 90)))
      .mockReturnValueOnce(of(week('2026-10-05', [entry(1, 60)], 60)));
    api.updateTimeEntry.mockReturnValue(of(entry(1, 60)));
    const store = setup();
    store.load('2026-10-05');
    store
      .update(1, {
        granularity: Granularity.Hour,
        startDate: '2026-10-05',
        startTime: '09:00',
        endTime: '10:00',
        activityTypeCode: 'REUNION',
        version: 0,
      })
      .subscribe();
    expect(api.updateTimeEntry.mock.calls[0][0]).toBe(1);
    expect(store.week()?.totalMinutes).toBe(60);
  });

  it('RG-01-11 : après suppression, la saisie sort du journal et du total', () => {
    api.listMyTimeEntries
      .mockReturnValueOnce(of(week('2026-10-05', [entry(1, 90), entry(2, 420)], 510)))
      .mockReturnValueOnce(of(week('2026-10-05', [entry(2, 420)], 420)));
    api.deleteTimeEntry.mockReturnValue(of(null));
    const store = setup();
    store.load('2026-10-05');
    store.remove(1).subscribe();
    expect(api.deleteTimeEntry).toHaveBeenCalledWith(1);
    expect(store.week()?.entries.map((e) => e.id)).toEqual([2]);
    expect(store.week()?.totalMinutes).toBe(420);
  });

  it('une suppression en attente n’envoie qu’une requête', () => {
    api.listMyTimeEntries.mockReturnValue(of(week('2026-10-05', [entry(1, 90)], 90)));
    api.deleteTimeEntry.mockReturnValue(new Subject());
    const store = setup();
    store.load('2026-10-05');
    store.remove(1).subscribe();
    store.remove(1).subscribe();
    expect(api.deleteTimeEntry).toHaveBeenCalledTimes(1);
    expect(store.pending.has(1)).toBe(true);
  });
});
