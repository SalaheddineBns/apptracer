import { Directive, effect, ElementRef, inject, input } from '@angular/core';

/**
 * Exclusive choice in a `p-menu` (language selector of the user menu, SFD-08 E1).
 *
 * `p-menu` hard-codes `role="menuitem"` on each `<li>` and `role="none"` on a submenu's title, with no option to
 * change either. This directive, placed on the content of the `#item` template, fixes the parent `<li>`:
 * - `role="menuitemradio"` and `aria-checked`: the screen reader announces the option and its state (checked or not);
 * - `lang`: the name is pronounced in its own language;
 * - the preceding title becomes a `role="group"` named by its text (`aria-labelledby`) that owns (`aria-owns`) the options.
 * The keyboard behaviour stays that of `p-menu` (arrows, Enter, Escape).
 */
@Directive({ selector: '[appMenuRadio]' })
export class MenuRadio {
  readonly checked = input.required<boolean>();
  readonly language = input<string>();
  /** `id` of the group title's text. */
  readonly groupLabelId = input.required<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    effect(() => {
      const item = this.host.closest('li');
      if (!item) {
        return;
      }
      item.setAttribute('role', 'menuitemradio');
      item.setAttribute('aria-checked', String(this.checked()));
      const language = this.language();
      if (language) {
        item.setAttribute('lang', language);
      }
      this.joinGroup(item);
    });
  }

  private joinGroup(item: HTMLElement): void {
    let group = item.previousElementSibling;
    while (group && group.getAttribute('data-pc-section') !== 'submenulabel') {
      group = group.previousElementSibling;
    }
    if (!group || !item.id) {
      return;
    }
    group.setAttribute('role', 'group');
    group.setAttribute('aria-labelledby', this.groupLabelId());
    const owned = new Set((group.getAttribute('aria-owns') ?? '').split(' ').filter(Boolean));
    owned.add(item.id);
    group.setAttribute('aria-owns', [...owned].join(' '));
  }
}
