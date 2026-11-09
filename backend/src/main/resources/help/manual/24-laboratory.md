# Laboratory
> Ordering tests, sample collection, results with reference ranges and critical flags, validation by a second person, and released reports.
<!-- audience: lab.order lab.read lab.collect lab.process lab.validate -->

Who uses it: doctors (order and read), phlebotomists (collect), laboratory technicians (receive and enter results) and the pathologist (validate). Opens from **Diagnostics → Laboratory**.

## Ordering tests (doctors)

On the patient's visit, **Investigations → Order tests**: tick the tests (and imaging if needed), choose the priority (routine, urgent, immediately) and add clinical notes. **Place orders** creates:

- one **accession number** per test (for example `VECLB2627000014`), printed on the sample label;
- one **bill** for the whole request, paid at reception or the counter.

## Working through the samples

The Laboratory page has a tab for each step:

| Tab | Who | What to do |
|---|---|---|
| To collect | Phlebotomist | Collect the sample in the right container (shown on the order) and mark **Sample collected** |
| To receive | Technician | Check the sample and **Receive in the lab**, or **Reject sample** with the reason (haemolysed, clotted, wrong container, unlabelled); a rejected sample goes back to *To collect* |
| To enter results | Technician | Enter every result and **Save results** |
| To validate | Pathologist | Review and **Validate and release** |
| Released | Everyone | Reports of the last 7 days |

Urgent and immediate orders are listed first.

## Flags and critical values

Each result is compared with the test's reference range: **High**, **Low**, or for text results **Abnormal** (for example *Reactive* where *Non-reactive* is normal). Values beyond the critical limits are flagged **Critical high** or **Critical low**: the order shows a red alert, and the treating doctor must be told at once.

## Validation (two people)

Results are released to the doctor only after **validation by someone other than the person who entered them**. The system refuses validation of your own results. Until then, doctors see the order and its status but not the values.

## Catalogue

The test list (sections, sample types, LOINC codes, units, reference and critical ranges, price, turnaround time) is kept for the whole organisation by holders of the catalogue role.
