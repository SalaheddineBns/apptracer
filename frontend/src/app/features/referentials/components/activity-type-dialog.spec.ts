import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { ActivityTypeResponse, ActivityTypesService, TagColor } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { ActivityTypeDialog } from './activity-type-dialog';

const existing = (over: Partial<ActivityTypeResponse> = {}): ActivityTypeResponse => ({
  id: 2,
  code: 'REUNION',
  label: 'Réunion',
  projectRequired: false,
  color: TagColor.Warn,
  active: true,
  version: 3,
  ...over,
});
const httpError = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));

describe('ActivityTypeDialog', () => {
  const createActivityType = vi.fn();
  const updateActivityType = vi.fn();
  const listActivityTypes = vi.fn();
  const add = vi.fn();
  const saved = vi.fn();
  const conflict = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<ActivityTypeDialog>>;

  async function open(type: ActivityTypeResponse | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivityTypesService, useValue: { createActivityType, updateActivityType, listActivityTypes } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    fixture = TestBed.createComponent(ActivityTypeDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('type', type);
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
  }

  const el = (cy: string) => document.body.querySelector<HTMLElement>(`[data-cy="${cy}"]`);
  const input = (cy: string) => el(cy) as HTMLInputElement;
  const error = (cy: string) => el(`erreur-${cy}`);
  async function type(cy: string, value: string): Promise<void> {
    input(cy).value = value;
    input(cy).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    el('btn-enregistrer')?.querySelector('button')?.click();
    await fixture.whenStable();
  }
  async function fillCreate(code = 'REUNION_CLIENT'): Promise<void> {
    await type('champ-code-type', code);
    await type('champ-libelle-type', 'Réunion client');
  }

  beforeEach(() => {
    for (const fn of [createActivityType, updateActivityType, listActivityTypes, add, saved, conflict]) {
      fn.mockReset();
    }
    listActivityTypes.mockReturnValue(of([]));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  it('création réussie : envoie code, libellé, couleur et projet requis, ferme et signale la création', async () => {
    createActivityType.mockReturnValue(of(existing({ id: 9, code: 'REUNION_CLIENT', label: 'Réunion client' })));
    await open(null);
    await fillCreate();
    fixture.componentInstance['form'].patchValue({ color: TagColor.Success, projectRequired: true });
    await submit();
    expect(createActivityType).toHaveBeenCalledWith({ code: 'REUNION_CLIENT', label: 'Réunion client', color: TagColor.Success, projectRequired: true });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create' }));
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(listActivityTypes).toHaveBeenCalled();
  });

  it('CA-02-03 : code déjà existant, l’erreur RG-02-01 s’affiche sous le champ code, dialogue ouvert et saisie conservée', async () => {
    createActivityType.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-02-01', detail: 'Ce code est déjà utilisé.', errors: [{ field: 'code', message: 'Ce code est déjà utilisé.' }] }),
    );
    await open(null);
    await fillCreate('REUNION');
    await submit();
    expect(error('champ-code-type')?.textContent).toContain('déjà utilisé');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(input('champ-code-type').value).toBe('REUNION');
    expect(input('champ-libelle-type').value).toBe('Réunion client');
    expect(add).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  it('CA-02-04 : le code « réunion client » met le champ en erreur, sans mise en majuscules, et bloque l’enregistrement', async () => {
    await open(null);
    await fillCreate('réunion client');
    await submit();
    expect(createActivityType).not.toHaveBeenCalled();
    expect(input('champ-code-type').value).toBe('réunion client');
    expect(error('champ-code-type')?.textContent).toContain('majuscules, chiffres et _');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it.each(['A', 'A'.repeat(31), 'AB-C'])('CA-02-04 : le code %s est refusé par le validateur', async (code) => {
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createActivityType).not.toHaveBeenCalled();
  });

  it.each(['AB', 'A'.repeat(30), 'REVUE_2'])('RG-02-01 : le code %s est accepté', async (code) => {
    createActivityType.mockReturnValue(of(existing()));
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createActivityType).toHaveBeenCalledTimes(1);
  });

  it('refuse un libellé vide ou de plus de 100 caractères', async () => {
    await open(null);
    await fillCreate();
    await type('champ-libelle-type', 'x'.repeat(101));
    await submit();
    expect(createActivityType).not.toHaveBeenCalled();
    expect(error('champ-libelle-type')?.textContent).toContain('100 caractères');
    await type('champ-libelle-type', '');
    await submit();
    expect(error('champ-libelle-type')?.textContent).toContain('obligatoire');
  });

  it('affiche sous le champ une erreur 400 du back sur label', async () => {
    createActivityType.mockReturnValue(httpError(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'label', message: 'Refusé par le serveur.' }] }));
    await open(null);
    await fillCreate();
    await submit();
    expect(error('champ-libelle-type')?.textContent).toContain('Refusé par le serveur.');
    expect(add).not.toHaveBeenCalled();
  });

  it('CA-02-05 : en modification, le code est affiché mais non modifiable, avec son aide, et absent de la requête', async () => {
    updateActivityType.mockReturnValue(of(existing({ label: 'Réunions', version: 4 })));
    await open(existing());
    expect(input('champ-code-type').disabled).toBe(true);
    expect(input('champ-code-type').value).toBe('REUNION');
    expect(el('dialogue-type-activite')?.textContent).toContain('Le code ne peut pas être modifié après la création.');
    await type('champ-libelle-type', 'Réunions');
    await submit();
    expect(updateActivityType).toHaveBeenCalledWith(2, { label: 'Réunions', color: TagColor.Warn, projectRequired: false, version: 3 });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'update' }));
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('CA-02-10 : le 409 RG-02-07 sur projectRequired s’affiche sous le champ projet requis, dialogue ouvert', async () => {
    updateActivityType.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-02-07', detail: 'Type utilisé.', errors: [{ field: 'projectRequired', message: 'Ce type est utilisé par des saisies : « projet requis » n’est plus modifiable.' }] }),
    );
    await open(existing());
    fixture.componentInstance['form'].patchValue({ projectRequired: true });
    await submit();
    expect(error('champ-projet-requis-type')?.textContent).toContain('n’est plus modifiable');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(add).not.toHaveBeenCalled();
  });

  it('modification concurrente : 409 sans code, le dialogue se ferme et la liste est rechargée', async () => {
    updateActivityType.mockReturnValue(httpError(409, { status: 409, type: 'https://apptracer/errors/concurrent-modification', detail: 'Ce type a été modifié entre-temps.' }));
    await open(existing());
    await submit();
    expect(conflict).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(saved).not.toHaveBeenCalled();
  });

  it('annulation : « Annuler » ferme sans appeler l’API', async () => {
    await open(null);
    await fillCreate();
    el('btn-annuler-dialogue')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(createActivityType).not.toHaveBeenCalled();
  });

  it('pendant l’envoi, le dialogue ne peut être fermé ni par Échap, ni par « Annuler », ni par la croix', async () => {
    createActivityType.mockReturnValue(new Subject());
    await open(null);
    await fillCreate();
    await submit();
    expect(createActivityType).toHaveBeenCalledTimes(1);
    expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
    expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    el('dialogue-type-activite')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('RG-02-04 : la liste de couleurs propose cinq options nommées en français, son nom accessible est « Couleur »', async () => {
    await open(null);
    const dialog = fixture.componentInstance as unknown as { colorOptions: () => { value: TagColor; label: string }[] };
    expect(dialog.colorOptions().map((o) => o.value)).toEqual(Object.values(TagColor));
    expect(dialog.colorOptions().map((o) => o.label)).toEqual(['Bleu', 'Orange', 'Gris', 'Contraste', 'Vert']);
    TestBed.inject(I18n).setLang('en');
    expect(dialog.colorOptions().map((o) => o.label)).toEqual(['Blue', 'Orange', 'Grey', 'Contrast', 'Green']);
    TestBed.inject(I18n).setLang('fr');
    const select = el('champ-couleur-type');
    const labelledBy = select?.querySelector('[role="combobox"]')?.getAttribute('aria-labelledby');
    expect(labelledBy).toBe('activity-type-color-label');
    expect(document.getElementById(labelledBy ?? '')?.textContent).toContain('Couleur');
  });

  it('l’aperçu reflète le libellé saisi et la couleur choisie', async () => {
    await open(null);
    await type('champ-libelle-type', 'Atelier');
    fixture.componentInstance['form'].patchValue({ color: TagColor.Success });
    await fixture.whenStable();
    const preview = el('apercu-type');
    expect(preview?.textContent).toContain('Atelier');
    expect(preview?.querySelector('.p-tag-success')).not.toBeNull();
  });

  it('la croix du dialogue a un nom accessible « Fermer »', async () => {
    await open(null);
    expect(document.body.querySelector('.p-dialog-close-button')?.getAttribute('aria-label')).toBe('Fermer');
  });

  it("ne reprend pas le focus à la fin de l'animation si la saisie a déjà commencé", async () => {
    await open(null);
    const dialog = fixture.componentInstance as unknown as { focusFirstField(form: HTMLFormElement): void };
    const form = el('dialogue-type-activite') as HTMLFormElement;
    input('champ-libelle-type').focus();
    dialog.focusFirstField(form);
    expect(document.activeElement).toBe(input('champ-libelle-type'));
    (document.activeElement as HTMLElement).blur();
    dialog.focusFirstField(form);
    expect(document.activeElement).toBe(input('champ-code-type'));
  });

  it('l’exemple cité par l’erreur du code est celui de l’aide du même champ', async () => {
    await open(null);
    await type('champ-code-type', 'minuscules');
    input('champ-code-type').dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    const example = (text: string | null | undefined) => /ex\. (\w+)\)/.exec(text ?? '')?.[1];
    const help = document.body.querySelector('[data-cy="champ-code-type"]')?.parentElement?.querySelector('small')?.textContent;
    expect(example(help)).toBe('REUNION_CLIENT');
    expect(example(el('erreur-champ-code-type')?.textContent)).toBe(example(help));
  });
});
