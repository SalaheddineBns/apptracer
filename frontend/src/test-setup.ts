// Les tests existants supposent un navigateur en français (RG-08-02) ; les tests de détection le surchargent explicitement.
// Remis à zéro avant chaque test : la langue choisie par un test ne doit pas fuir vers le suivant.
beforeEach(() => {
  Object.defineProperty(window.navigator, 'language', { value: 'fr-FR', configurable: true });
  Object.defineProperty(window.navigator, 'languages', { value: ['fr-FR', 'fr'], configurable: true });
  localStorage.removeItem('lang');
});
