import { Directive, effect, ElementRef, inject, input } from '@angular/core';

/**
 * Choix exclusif dans un `p-menu` (sélecteur de langue du menu utilisateur, SFD-08 E1).
 *
 * `p-menu` pose en dur `role="menuitem"` sur chaque `<li>` et `role="none"` sur le titre d'un sous-menu, sans option pour
 * changer l'un ou l'autre. Cette directive, posée sur le contenu du gabarit `#item`, corrige le `<li>` parent :
 * - `role="menuitemradio"` et `aria-checked` : le lecteur d'écran annonce le choix et son état (coché ou non) ;
 * - `lang` : le nom est prononcé dans sa propre langue ;
 * - le titre qui précède devient un `role="group"` nommé par son texte (`aria-labelledby`) qui possède (`aria-owns`) les choix.
 * Le clavier reste celui de `p-menu` (flèches, Entrée, Échap).
 */
@Directive({ selector: '[appMenuRadio]' })
export class MenuRadio {
  readonly checked = input.required<boolean>();
  readonly language = input<string>();
  /** `id` du texte du titre du groupe. */
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
