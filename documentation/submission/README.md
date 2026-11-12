# Afleveringspakke · AJ Motors

Rapporten er fælles for de tre opgavedokumenter. Aflever de genererede filer,
ikke kun Markdown-links til filer på din computer.

[Hent den samlede afleveringspakke](aj-motors-delivery.zip). Den indeholder rapport,
begge PowerPoints, PDF-previews, instruktioner og en [kildekode-ZIP](aj-motors-source.zip)
til overdragelse uden terminal-login. Kildekode-ZIP’en udelader lokale hemmeligheder,
testdata, caches og build-output. Den er ikke bevis for, at GitHub er opdateret.

| Aflevering | Filer og link |
| --- | --- |
| Mandatory Assignment 1 · itsLearning | [Rapport](../Report.pdf), [PowerPoint](mandatory-assignment-1.pptx), [GitHub-monorepo](https://github.com/AhmadAlkaseb/aj) |
| Mandatory Assignment 2 · itsLearning | [Rapport](../Report.pdf), [PowerPoint](mandatory-assignment-2.pptx), [GitHub-monorepo](https://github.com/AhmadAlkaseb/aj) |
| Final Project · WISEflow | [Rapport med bilag A–E](../Report.pdf), [GitHub-monorepo](https://github.com/AhmadAlkaseb/aj), [installationsprocedure](../LOCAL_SETUP.md) og [Kubernetes](../KUBERNETES.md) |

Forhåndsvis præsentationerne som [Assignment 1 PDF](mandatory-assignment-1.pdf)
og [Assignment 2 PDF](mandatory-assignment-2.pdf). Hver præsentation har ni slides
og redigerbare tekstfelter; PowerPoint-filerne indeholder talernoter med kildehenvisninger.
De beskriver den aktuelle tekniske løsning, ikke en opdigtet tidligere fremlæggelse.

## Kontrolleret og klargjort lokalt

- Rapportens servicebeskrivelser omfatter ansvar, API/eventvalg, dataejerskab og fejl/skalering.
- Compose-modellen viser 24 services og eksplicit GUI-administration; Kubernetes-standby er beskrevet.
- Rapporten har change-aware CI/CD, seks Grafana-dashboards og inkluderede bilag A–E.
- Cloudplanen vælger AKS og placerer identitet, AI, jobs, broker, databaser og observability.
- Arbejdsmodellen fordeler fire workstreams og cross-review balanceret; personlige afsnit er tydeligt refleksionsudkast.
- Kode til rigtig GitHub-federation og dens konfigurations-/sikkerhedstests er tilføjet.

## Eksterne trin, der ikke kan erstattes med lokal test

- [ ] Registrér en OAuth-app på en GitHub-konto, I ejer, og kør
  `python scripts/configure_federation.py setup`; gennemfør derefter et rigtigt
  GitHub-login. [Præcis procedure](../EXTERNAL-LOGIN.md). Der kræves ingen GitHub CLI.
- [ ] Få den færdige kildekode op på repositoryet og gem faktiske CI-run-links.
  Lokal validering er et dokumenteret alternativ i denne session, ikke et GitHub-run.
- [ ] Hver deltager godkender eget refleksionsudkast og sikrer autentisk synligt bidrag.
  Den inspicerede HEAD-historik har Ahmad/Jeppe-forfatteridentiteter; andre bidrag
  må dokumenteres fra virkelige branches/PRs eller korrekte arbejdsbeskrivelser.
  Opfind ikke commits, timer eller mødehistorik.
- [ ] Bekræft grupperegistrering og repository-link i kursets OneDrive-ark.
- [ ] Upload de relevante filer i itsLearning/WISEflow og kontrollér kvitteringen.

De eksterne trin er konkrete udeståender. De bliver ikke opfyldt af en ekstra figur,
en PowerPoint eller lokal grøn test. Der er ikke oprettet betalte cloudressourcer.

## Genskab dokumenterne

```powershell
python -m pip install -r scripts/requirements-report.txt
python scripts/render_architecture_models.py
python scripts/render_assignment_presentations.py
python scripts/export_report_latex.py
python scripts/check_submission.py
python scripts/build_submission_bundle.py
```

Pandoc/Tectonic sættes op med `./scripts/setup_report.ps1`; Playwright kræver installeret
Chromium. Rapporteksporten kontrollerer højst 30 PDF-sider og 72.000 kildetegn,
unummereret forside, sidetal og figur-/indholdsfortegnelser.
