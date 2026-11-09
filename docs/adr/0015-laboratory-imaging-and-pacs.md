# ADR-0015: Laboratory, imaging and an open-source image archive and viewer

Status: accepted (2026-11-09)

## Decision
- **One diagnostic order model** for laboratory and imaging: accession number per test (also the
  DICOM AccessionNumber), one bill per request, status flow per kind. Lab results carry LOINC codes
  and are flagged against reference and critical ranges; release needs validation by a second person
  (DB CHECK). Imaging reports are released by signature (radiology.verify).
- **Image archive: Orthanc** (open source, DICOM C-STORE on 4242, DICOMweb) with its **OHIF viewer
  plugin**, one container (`orthancteam/orthanc:26.9.1`). The backend finds a study by accession
  number over DICOMweb (QIDO) on the internal network.
- **Access to images goes only through the gateway** at `/api/pacs/` (under the API path so the
  session cookie applies). Every request is authorised by the backend (`auth_request` →
  `/v1/imaging/authorize`): viewer files for imaging staff; DICOMweb only for a named study that
  belongs to an imaging order at a centre where the user holds an imaging permission. No archive
  search, no Orthanc UI or REST, GET/HEAD only. Answers are cached for a minute per user and study.
- **Tenancy:** one Orthanc serves all chains on an installation; isolation is by this per-study check
  (the study must belong to an order in the user's own tenant, under row-level security). A chain that
  needs physically separate images gets its own installation.

## Not yet
Modality worklist (MWL), HL7 or ASTM interfaces to lab analysers, report templates, PDF reports with
letterhead, Orthanc storage on PostgreSQL or object storage, image backup and retention policy.
