import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { Granularity } from '../../../core/api/generated';
import { addDays, isoToLocalDate, todayInParis } from '../../../shared/dates/week';
import { TimeEntryDialog } from './time-entry-dialog';
import { timeStringToDate } from './time-entry-period';

const date = (iso: string) => isoToLocalDate(iso);
const time = (value: string) => timeStringToDate(value);

describe('TimeEntryDialog', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TimeEntryDialog>>;

  async function open(): Promise<void> {
    fixture = TestBed.createComponent(TimeEntryDialog);
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
  }

  const form = () => (fixture.componentInstance as unknown as { form: FormGroup }).form;
  const el = (cy: string) => document.body.querySelector<HTMLElement>(`[data-cy="${cy}"]`);
  const error = (cy: string) => el(`erreur-${cy}`)?.textContent ?? null;
  async function patch(value: Record<string, unknown>): Promise<void> {
    form().patchValue(value);
    form().markAllAsTouched();
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  describe('période', () => {
    it('SFD-01 E2 : propose heure, journée et plage ; en HEURE, la date et les deux heures', async () => {
      await open();
      expect(el('select-granularite')?.textContent).toContain('Heure');
      expect(el('select-granularite')?.textContent).toContain('Journée');
      expect(el('select-granularite')?.textContent).toContain('Plage');
      expect(form().get('granularity')?.value).toBe(Granularity.Hour);
      expect(el('champ-date-debut')).not.toBeNull();
      expect(el('champ-heure-debut')).not.toBeNull();
      expect(el('champ-heure-fin')).not.toBeNull();
      expect(el('champ-date-fin')).toBeNull();
    });

    it('RG-01-02 : en JOURNEE, seule la date est demandée', async () => {
      await open();
      await patch({ granularity: Granularity.Day, startDate: date('2026-10-05') });
      expect(el('champ-heure-debut')).toBeNull();
      expect(el('champ-heure-fin')).toBeNull();
      expect(el('champ-date-fin')).toBeNull();
      expect(form().get('startTime')?.disabled).toBe(true);
      expect(form().get('endDate')?.disabled).toBe(true);
      expect(form().get('startDate')?.valid).toBe(true);
    });

    it('RG-01-03 : en PLAGE, la date de fin remplace les heures', async () => {
      await open();
      await patch({ granularity: Granularity.Range });
      expect(el('champ-date-fin')).not.toBeNull();
      expect(el('champ-heure-debut')).toBeNull();
      expect(el('dialogue-saisie')?.textContent).toContain('Date de début');
      expect(form().get('endTime')?.disabled).toBe(true);
    });

    it('CA-01-02 : une heure de fin antérieure à l’heure de début met le champ en erreur', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('10:30'), endTime: time('09:00') });
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
      expect(form().invalid).toBe(true);
    });

    it('RG-01-01 : une heure de fin égale à l’heure de début est refusée, puis acceptée dès que le début recule', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('09:00'), endTime: time('09:00') });
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
      await patch({ startTime: time('08:45') });
      expect(error('champ-heure-fin')).toBeNull();
      expect(form().get('endTime')?.valid).toBe(true);
    });

    it('RG-01-01 : une heure hors du pas de 15 minutes est refusée telle que saisie', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('09:10'), endTime: time('10:00') });
      expect(error('champ-heure-debut')).toContain('pas de 15 minutes');
      expect((form().get('startTime')?.value as Date).getMinutes()).toBe(10);
    });

    it('RG-01-01 : un créneau jusqu’à 23:45 est accepté, minuit ne l’est pas', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('22:00'), endTime: time('23:45') });
      expect(form().get('endTime')?.valid).toBe(true);
      await patch({ endTime: time('00:00') });
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
    });

    it('CA-01-07 : une date de début dans le futur est refusée', async () => {
      await open();
      await patch({ startDate: date(addDays(todayInParis(), 1)) });
      expect(error('champ-date-debut')).toContain('ne peut pas être dans le futur');
      expect(form().invalid).toBe(true);
    });

    it('RG-01-07 : la date du jour est acceptée, et une PLAGE peut se terminer dans le futur', async () => {
      await open();
      const today = todayInParis();
      await patch({
        granularity: Granularity.Range,
        startDate: date(today),
        endDate: date(addDays(today, 3)),
      });
      expect(form().get('startDate')?.valid).toBe(true);
      expect(form().get('endDate')?.valid).toBe(true);
    });

    it('RG-01-03 : la date de fin doit être après la date de début', async () => {
      await open();
      await patch({
        granularity: Granularity.Range,
        startDate: date('2026-10-05'),
        endDate: date('2026-10-05'),
      });
      expect(error('champ-date-fin')).toContain('après la date de début');
    });

    it('RG-01-03 : 31 jours bornes incluses sont acceptés, 32 refusés', async () => {
      await open();
      await patch({
        granularity: Granularity.Range,
        startDate: date('2026-10-01'),
        endDate: date('2026-10-31'),
      });
      expect(form().get('endDate')?.valid).toBe(true);
      await patch({ endDate: date('2026-11-01') });
      expect(error('champ-date-fin')).toContain('au plus 31 jours');
    });

    it('les champs obligatoires de la période sont signalés', async () => {
      await open();
      await patch({ startDate: null });
      expect(error('champ-date-debut')).toContain('obligatoire');
      expect(error('champ-heure-debut')).toContain('obligatoire');
      expect(error('champ-heure-fin')).toContain('obligatoire');
    });

    it('RG-01-09 : un commentaire de 500 caractères est accepté, 501 refusé sans être tronqué', async () => {
      await open();
      await patch({ comment: 'x'.repeat(500) });
      expect(form().get('comment')?.valid).toBe(true);
      await patch({ comment: 'x'.repeat(501) });
      expect(error('champ-commentaire')).toContain('500 caractères');
      expect((form().get('comment')?.value as string).length).toBe(501);
    });

    it('« Annuler » ferme le dialogue', async () => {
      await open();
      el('btn-annuler-dialogue')?.querySelector('button')?.click();
      await fixture.whenStable();
      expect(fixture.componentInstance.visible()).toBe(false);
    });
  });
});
