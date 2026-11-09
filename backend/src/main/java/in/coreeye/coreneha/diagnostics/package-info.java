/**
 * Laboratory and imaging (LAB-01..LAB-12, RAD-01..RAD-10). Test and procedure catalogues per chain
 * (LOINC codes, reference and critical ranges), diagnostic orders with an accession number and a
 * bill, sample collection, receipt or rejection, results with automatic flags, validation by a
 * second person, imaging schedule and acquisition linked to the DICOM study in the archive
 * (Orthanc), reports and signature. Images are viewed in the OHIF viewer through the gateway, which
 * asks {@link in.coreeye.coreneha.diagnostics.ImagingAccess} before every request: a user sees a
 * study only if it belongs to an order at a centre where they hold radiology.read.
 */
package in.coreeye.coreneha.diagnostics;
