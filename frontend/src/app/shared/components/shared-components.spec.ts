import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { By } from '@angular/platform-browser';
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

  it('LoadingState : affiche le nombre de squelettes demandé', async () => {
    const fixture = TestBed.createComponent(LoadingState);
    fixture.componentRef.setInput('rows', 3);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('p-skeleton').length).toBe(3);
  });
});
