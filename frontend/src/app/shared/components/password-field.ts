import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { FieldError } from './field-error';

/**
 * Champ mot de passe : libellé, bouton « Afficher / Masquer » (focalisable, nom accessible), saisie,
 * texte d'aide et erreur sous le champ. Partagé par la connexion et le changement de mot de passe.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, FieldError],
  selector: 'app-password-field',
  template: `
    <div class="flex flex-col gap-2">
      <div class="flex items-center justify-between gap-2">
        <label [for]="inputId()" class="text-sm font-medium text-color">{{ label() }} <span aria-hidden="true">*</span></label>
        <p-button
          type="button"
          [text]="true"
          size="small"
          [label]="visible() ? 'Masquer' : 'Afficher'"
          [ariaLabel]="(visible() ? 'Masquer ' : 'Afficher ') + subject()"
          (onClick)="visible.set(!visible())"
          [attr.data-cy]="toggleCy()"
        />
      </div>
      <input
        pInputText
        [id]="inputId()"
        [type]="visible() ? 'text' : 'password'"
        [formControl]="control()"
        [autocomplete]="autocomplete()"
        [attr.aria-describedby]="hint() ? inputId() + '-hint' : null"
        fluid
        required
        [attr.data-cy]="fieldCy()"
      />
      @if (hint()) {
        <small class="text-xs text-muted-color" [id]="inputId() + '-hint'">{{ hint() }}</small>
      }
      <app-field-error [control]="control()" [messages]="messages()" [cy]="'erreur-' + fieldCy()" />
    </div>
  `,
})
export class PasswordField {
  readonly control = input.required<FormControl<string>>();
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly autocomplete = input.required<string>();
  readonly fieldCy = input.required<string>();
  readonly toggleCy = input.required<string>();
  readonly messages = input<Record<string, string>>({});
  readonly hint = input<string | null>(null);
  /** Complément du libellé accessible du bouton : « Afficher le mot de passe ». */
  readonly subject = input('le mot de passe');

  protected readonly visible = signal(false);
}
