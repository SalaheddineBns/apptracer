import { ChangeDetectionStrategy, Component, effect, input, signal } from '@angular/core';
import { FormControl, FormResetEvent, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TPipe } from '../../core/i18n/t.pipe';
import { FieldError, FieldMessages } from './field-error';

/**
 * Password field: label, Show / Hide button (focusable, accessible name), input,
 * help text and error under the field. Shared by sign-in and password change.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, FieldError, TPipe],
  selector: 'app-password-field',
  template: `
    <div class="flex flex-col gap-2">
      <div class="flex items-center justify-between gap-2">
        <label [for]="inputId()" class="text-sm font-medium text-color">{{ label() }} <span aria-hidden="true">*</span></label>
        <p-button
          type="button"
          [text]="true"
          size="small"
          [label]="(visible() ? 'password.hide' : 'password.show') | t"
          [ariaLabel]="(visible() ? 'password.hideAria' : 'password.showAria') | t: { subject: subject() }"
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
  readonly messages = input<FieldMessages>({});
  readonly hint = input<string | null>(null);
  /** Complement (already translated) of the button's accessible label: 'Show the password'. */
  readonly subject = input.required<string>();

  protected readonly visible = signal(false);

  constructor() {
    // A password is shown only on explicit request: any form reset hides it again.
    effect((onCleanup) => {
      const subscription = this.control().events.subscribe((event) => {
        if (event instanceof FormResetEvent) {
          this.visible.set(false);
        }
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
