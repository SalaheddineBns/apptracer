import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { I18n } from '../../core/i18n/i18n.service';
import { applyFieldErrors } from '../forms/apply-field-errors';
import { EmptyState } from './empty-state';
import { ErrorState } from './error-state';
import { FieldError } from './field-error';
import { LoadingState } from './loading-state';

@Component({
  imports: [EmptyState],
  template: `<app-empty-state message="Aucune activité"><button data-cy="action">Ajouter</button></app-empty-state>`,
})
class EmptyHost {}

describe('composants partagés', () => {
  it('FieldError : masque l’erreur avant blur puis l’affiche', async () => {
    const control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
    const fixture = TestBed.createComponent(FieldError);
    fixture.componentRef.setInput('control', control);
    fixture.componentRef.setInput('messages', { required: 'login.errors.emailRequired' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toBe('');

    control.markAsTouched();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('L’e-mail est obligatoire.');
    expect(fixture.nativeElement.querySelector('[data-cy="field-error"]').className).toContain('text-red-700');
  });

  it('FieldError : affiche immédiatement une erreur serveur posée par applyFieldErrors', async () => {
    const form = new FormGroup({ email: new FormControl('') });
    const fixture = TestBed.createComponent(FieldError);
    fixture.componentRef.setInput('control', form.controls.email);
    await fixture.whenStable();

    const unmatched = applyFieldErrors(form, [
      { field: 'email', message: 'Adresse déjà utilisée' },
      { field: 'inconnu', message: 'm' },
    ]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Adresse déjà utilisée');
    expect(unmatched).toEqual([{ field: 'inconnu', message: 'm' }]);
  });

  it('RG-08-04 : FieldError efface une erreur serveur au changement de langue, et garde les erreurs de validation', async () => {
    const control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
    const fixture = TestBed.createComponent(FieldError);
    fixture.componentRef.setInput('control', control);
    fixture.componentRef.setInput('messages', { required: 'login.errors.emailRequired' });
    control.markAsTouched();
    control.setErrors({ server: 'Invalid email' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Invalid email');

    TestBed.inject(I18n).setLang('en');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Invalid email');
    expect(fixture.nativeElement.textContent).toContain('The email is required.');
  });

  it('EmptyState : affiche le message et l’action projetée', async () => {
    const fixture = TestBed.createComponent(EmptyHost);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Aucune activité');
    expect(fixture.nativeElement.querySelector('[data-cy="action"]')).not.toBeNull();
  });

  it('ErrorState : émet retry au clic sur « Réessayer »', async () => {
    const fixture = TestBed.createComponent(ErrorState);
    const retry = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);
    await fixture.whenStable();
    fixture.debugElement.query(By.css('[data-cy="btn-reessayer"] button')).nativeElement.click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('RG-08-04 : ErrorState remplace le message du serveur, resté dans l’ancienne langue, par le message générique traduit', async () => {
    const fixture = TestBed.createComponent(ErrorState);
    fixture.componentRef.setInput('message', 'Service indisponible.');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Service indisponible.');

    TestBed.inject(I18n).setLang('en');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Unable to load the data.');
    expect(fixture.nativeElement.textContent).not.toContain('Service indisponible.');

    // Un nouveau message reçu dans la langue courante est affiché tel quel.
    fixture.componentRef.setInput('message', 'Service unavailable.');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Service unavailable.');
  });

  it('LoadingState : affiche le nombre de squelettes demandé', async () => {
    const fixture = TestBed.createComponent(LoadingState);
    fixture.componentRef.setInput('rows', 3);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBe(3);
  });
});
