import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { errorInterceptor, SKIP_ERROR_TOAST } from './error.interceptor';
import { toProblem } from './problem';

describe('errorInterceptor', () => {
  const add = vi.fn();
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    add.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MessageService, useValue: { add } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  function fail(body: object, status: number, context?: HttpContext): void {
    let relayed = false;
    http.get('/api/v1/x', { context }).subscribe({ error: () => (relayed = true) });
    controller.expectOne('/api/v1/x').flush(body, { status, statusText: 'err' });
    expect(relayed).toBe(true);
  }

  it('affiche le detail du ProblemDetail dans un toast et relaie l’erreur', () => {
    fail({ detail: 'Compte déjà existant', code: 'RG-03-01' }, 409);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'Compte déjà existant' }));
  });

  it('CA-05-07 : un 409 RG-05-10 sans errors[] s’affiche en toast avec le détail, qui nomme l’équipe', () => {
    const detail = 'Ce compte est manager de l’équipe « Produit ». Désignez un autre manager ou supprimez l’équipe avant de le désactiver.';
    fail({ detail, code: 'RG-05-10' }, 409);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail }));
    expect(add.mock.calls[0][0].detail).toContain('« Produit »');
  });

  it('n’affiche pas de toast pour un 401 (traité par l’intercepteur JWT)', () => {
    fail({ detail: 'Non authentifié' }, 401);
    expect(add).not.toHaveBeenCalled();
  });

  it('n’affiche pas de toast quand errors[] est présent (affiché sous les champs)', () => {
    fail({ detail: 'Requête invalide', errors: [{ field: 'email', message: 'Format invalide' }] }, 400);
    expect(add).not.toHaveBeenCalled();
  });

  it('n’affiche pas de toast si SKIP_ERROR_TOAST est activé', () => {
    fail({ detail: 'x' }, 409, new HttpContext().set(SKIP_ERROR_TOAST, true));
    expect(add).not.toHaveBeenCalled();
  });

  it('affiche un message générique si le serveur est injoignable', () => {
    let relayed = false;
    http.get('/api/v1/x').subscribe({ error: () => (relayed = true) });
    controller.expectOne('/api/v1/x').error(new ProgressEvent('error'));
    expect(relayed).toBe(true);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ detail: expect.stringContaining('injoignable') }));
  });

  it('toProblem lit code et errors[] à plat dans le corps', () => {
    let problem = null as ReturnType<typeof toProblem> | null;
    http.get('/api/v1/x').subscribe({ error: (e: unknown) => (problem = toProblem(e)) });
    controller
      .expectOne('/api/v1/x')
      .flush({ detail: 'd', code: 'RG-03-15', errors: [{ field: 'oldPassword', message: 'm' }, { bad: 1 }] }, { status: 400, statusText: 'x' });
    expect(problem).toEqual({ status: 400, detail: 'd', code: 'RG-03-15', type: null, fieldErrors: [{ field: 'oldPassword', message: 'm' }] });
  });
});
