import { TestBed } from '@angular/core/testing';
import { TagColor } from '../../core/api/generated';
import { iconForCode, severityForColor } from '../activity-type.ui';
import { ActivityTypeTag } from './activity-type-tag';

async function render(label: string, color: TagColor, code: string): Promise<HTMLElement> {
  const fixture = TestBed.createComponent(ActivityTypeTag);
  fixture.componentRef.setInput('label', label);
  fixture.componentRef.setInput('color', color);
  fixture.componentRef.setInput('code', code);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('ActivityTypeTag', () => {
  it.each([
    [TagColor.Info, 'info'],
    [TagColor.Warn, 'warn'],
    [TagColor.Secondary, 'secondary'],
    [TagColor.Contrast, 'contrast'],
    [TagColor.Success, 'success'],
  ] as const)('RG-02-04 : la couleur %s donne la severity %s', async (color, severity) => {
    expect(severityForColor(color)).toBe(severity);
    const el = await render('Libellé', color, 'X');
    expect(el.querySelector('.p-tag')?.className).toContain(`p-tag-${severity}`);
  });

  it.each([
    ['TRAVAIL_PROJET', 'pi-briefcase'],
    ['REUNION', 'pi-users'],
    ['LECTURE_SFD', 'pi-book'],
    ['REVUE_MR', 'pi-code'],
    ['FORMATION', 'pi-graduation-cap'],
    ['AUTRE', 'pi-ellipsis-h'],
  ])('RG-02-04 : le code %s a l’icône %s', async (code, icon) => {
    expect(iconForCode(code)).toBe(`pi ${icon}`);
    const el = await render('Libellé', TagColor.Info, code);
    expect(el.querySelector(`.${icon}`)).not.toBeNull();
  });

  it('RG-02-04 : un type créé par l’ADMIN s’affiche sans icône, avec son libellé', async () => {
    expect(iconForCode('REUNION_CLIENT')).toBeUndefined();
    expect(iconForCode('constructor')).toBeUndefined();
    const el = await render('Réunion client', TagColor.Warn, 'REUNION_CLIENT');
    expect(el.querySelector('.pi')).toBeNull();
    expect(el.textContent).toContain('Réunion client');
  });

  it('RG-02-04 : le libellé est toujours affiché en clair', async () => {
    const el = await render('Revue de MR', TagColor.Contrast, 'REVUE_MR');
    expect(el.querySelector('[data-cy="activity-type-tag"]')?.textContent).toContain('Revue de MR');
  });
});
