# Imaging
> X-ray, CT, MRI, ultrasound, OCT and fundus imaging: ordering, scheduling, linking the images from the machine, the image viewer, reports and signature.
<!-- audience: radiology.order radiology.read radiology.perform radiology.report radiology.verify -->

Who uses it: doctors (order, read, view images), radiographers and imaging technicians (schedule and perform), and radiologists (report and sign). Opens from **Diagnostics → Imaging**.

## Ordering

As for laboratory tests: **Investigations → Order tests** on the visit, tick the imaging (for example *OCT macula*, *Fundus photography*, *CT orbits*, *MRI orbits and brain*, *Chest X-ray*). Each gets an **accession number** such as `VECRD2627000006`.

## Performing the study

1. **To schedule**: open the order, choose the date and time and **Schedule**. Preparation instructions are on the procedure.
2. On the machine (OCT, fundus camera, X-ray, CT, MRI, ultrasound), enter the patient and **the accession number exactly as shown**. The machine sends the images to the image archive.
3. **Study done: link images**. The system finds the images in the archive by the accession number and links them to the order. If the images have not arrived yet, it says so; try again in a minute.

## Viewing images

**Open images in the viewer** (or *Images* on the visit chart) opens the study in the OHIF viewer in a new tab: scroll through slices, zoom, pan, adjust window and level, measure. You can open only studies that belong to orders at centres where you work with imaging, and only one study at a time; the archive cannot be searched or browsed from outside.

## Reporting (radiologists)

**To report**: write the findings and the impression and **Save report**. **To sign**: review and **Sign and release**; the report then reaches the doctor and the patient record.

## Connecting a machine (IT)

| Setting on the machine | Value |
|---|---|
| Destination (C-STORE) AE title | `CORENEHA` |
| Host | the server's address on the clinic network |
| Port | `4242` (set `CORE_NEHA_DICOM_BIND` to the server's network address to accept machines) |

Machines that cannot send DICOM (some fundus cameras) can export DICOM files for IT to import. A modality worklist (sending patient details to the machine automatically) is planned.
