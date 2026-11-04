# Generates the Vijaya Eye Clinic website pages (static HTML) into frontend/website/.
# Run: python3 frontend/scripts/build_website.py
import os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'website'))
MARK = '''<svg viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="22.5" fill="#0a2540"/><path d="M8 24c4.2-6.6 9.6-10 16-10s11.8 3.4 16 10c-4.2 6.6-9.6 10-16 10S12.2 30.6 8 24z" fill="none" stroke="#fff" stroke-width="2.2" stroke-linejoin="round"/><circle cx="24" cy="24" r="6.2" fill="#0066cc"/><circle cx="24" cy="24" r="2.5" fill="#0a2540"/><circle cx="26.4" cy="21.6" r="1.3" fill="#b8862b"/></svg>'''

NAV = [('specialities', 'Specialities'), ('doctors', 'Doctors'), ('patients', 'Patients and visitors'), ('contact', 'Contact')]

DOCTORS = [
    ('VP', 'Dr. Vasant N Patil', 'Founder and Chief Ophthalmologist', 'MBBS, DO, MS (Ophthalmology), FVR', '40+ years',
     'Comprehensive ophthalmology, cataract and glaucoma.'),
    ('V', 'Dr. Vishwanath', 'Senior Vitreo-Retinal Surgeon', 'MBBS, DO, MS (Ophthalmology), FVR', '25+ years',
     'Vitreo-retinal surgery, diabetic retinopathy and macular disorders.'),
    ('SK', 'Dr. Sunil Kumar M', 'Consultant Ophthalmologist and Phaco Surgeon', 'MBBS, DOMS, DNB', '15+ years',
     'Phacoemulsification cataract surgery and refractive care.'),
    ('SC', 'Dr. Savitha C S', 'Consultant Ophthalmologist', 'MS (Ophthalmology), FVR', '20+ years',
     'Anterior segment, medical retina and diagnostics.'),
    ('CK', 'Dr. Chandana K M', 'Consultant Ophthalmologist', 'MBBS, MS (Ophthalmology)', '10+ years',
     'Cornea care and eye examinations for children.'),
    ('DK', 'Dr. Dhananjaya K H', 'Consultant Cataract, Cornea and Refractive Surgeon', 'MS (Ophthalmology), FPRS', '8+ years',
     'Phacoemulsification, cornea and LASIK.'),
]

SPECIALITIES = [
    ('cataract', 'Cataract surgery', 'Stitchless micro-incision phacoemulsification as day-care surgery, with monofocal, toric and multifocal lens choices.'),
    ('lasik', 'LASIK and refractive surgery', 'Laser vision correction planned from corneal topography and thickness, for adults who want to be free of glasses or contact lenses.'),
    ('retina', 'Retina and diabetic eye care', 'Diabetic retinopathy, macular degeneration and retinal detachment, with OCT, angiography and retinal laser.'),
    ('glaucoma', 'Glaucoma', 'Early detection of the "silent thief of sight" with pressure, nerve and visual field tests, and lifelong management.'),
    ('children', 'Children\'s eye care and squint', 'Refractive errors, lazy eye (amblyopia), squint and digital eye strain, assessed in a child-friendly way.'),
    ('cornea', 'Cornea, dry eye and eye examinations', 'Complete eye examinations, dry eye and tear-film assessment, pterygium and corneal infections, and in-house spectacles.'),
]

INSURERS = ['Star Health Insurance', 'HDFC ERGO', 'ICICI Lombard', 'Bajaj Allianz', 'Niva Bupa', 'Care Health Insurance',
            'United India Insurance', 'National Insurance', 'The Oriental Insurance', 'The New India Assurance', 'ManipalCigna',
            'Medi Assist TPA', 'Vidal Health TPA', 'Paramount Health', 'Heritage Health', 'Family Health Plan (FHPL)']

FACILITIES = [
    ('Micro-incision phacoemulsification', 'Cataract removal through an incision of about 2.2 mm, for a quicker recovery.'),
    ('High-definition OCT', 'Cross-sectional scans of the retina, macula and optic nerve.'),
    ('Fundus fluorescein angiography (FFA)', 'Maps blood flow in the retina, and leakage from diabetic eye disease.'),
    ('Automated visual field testing', 'Humphrey field analysis to detect early loss of side vision in glaucoma.'),
    ('Non-contact tonometry and pachymetry', 'Eye pressure and corneal thickness, measured without touching the eye.'),
    ('Modular operation theatres', 'Sterile suites with HEPA filtration, operating microscopes and autoclave sterilisation.'),
]

FAQ = [
    ('Where is the clinic?', 'At #2, PV Plaza, 5th Main Road, MC Layout, Vijayanagar, opposite Sarvajna Bus Stop near the Govindarajanagar signal. It is about five minutes from Vijayanagar Metro Station, and BMTC buses from Majestic, Market and Rajajinagar stop in front of the clinic.'),
    ('Do I need an appointment?', 'Walk-ins are welcome during clinic hours. A quick call or WhatsApp message helps us confirm the doctor\'s timings and shortens your wait.'),
    ('How long does a consultation take?', 'Usually one to two hours, including vision and pressure tests. If the doctor dilates your pupils to examine the retina, allow more time and do not drive yourself home.'),
    ('Is cataract surgery done as day-care?', 'Yes. After optical biometry to choose the lens, phacoemulsification is done as day-care surgery, with monofocal, toric and multifocal lenses from Zeiss and Alcon. You go home the same day and come back for a review.'),
    ('Can I find out whether LASIK suits me?', 'Yes. Pre-LASIK tests include corneal topography, corneal thickness (pachymetry) and a dilated retina examination.'),
    ('Is cashless treatment available?', 'Yes, with the insurers and TPAs listed on this website. Our Mediclaim desk helps with the documents and the pre-authorisation.'),
    ('Do you see children?', 'Yes: children\'s consultations, screening for refractive errors, treatment for lazy eye (amblyopia) and squint evaluation.'),
]

URGENT = '''<aside class="urgent" aria-labelledby="urgent-title">
          <h3 id="urgent-title">When to seek help immediately</h3>
          <ul>
            <li>Sudden loss or dimming of vision in one or both eyes.</li>
            <li>A sudden shower of floaters, flashes of light, or a curtain over part of your vision.</li>
            <li>An injury to the eye, or a chemical splash: rinse with clean water for 15 minutes on the way.</li>
            <li>A painful red eye with headache, nausea or haloes around lights.</li>
          </ul>
          <p class="muted" style-free>Call the clinic, or go to the nearest emergency department.</p>
        </aside>'''.replace(' style-free', '')

CONTACT_FACTS = '''<dl class="facts">
            <div class="row"><dt>Address</dt><dd data-site="address"></dd></div>
            <div class="row" data-site-optional><dt>Landmark</dt><dd data-site="landmark"></dd></div>
            <div class="row" data-site-optional><dt>Timings</dt><dd data-site="hours"></dd></div>
            <div class="row" data-site-optional><dt>Landline</dt><dd><a href="#" data-site="phone"></a></dd></div>
            <div class="row" data-site-optional><dt>Mobile and WhatsApp</dt><dd><a href="#" data-site="mobile"></a></dd></div>
            <div class="row" data-site-optional><dt>Email</dt><dd><a href="#" data-site="email"></a></dd></div>
          </dl>
          <div class="contact-actions">
            <a class="button" href="#" data-site="bookingUrl">Book an appointment</a>
            <a class="button quiet" href="#" data-site="whatsapp" data-keep-text>Message on WhatsApp</a>
            <a class="button quiet" href="#" data-site="mapUrl">Get directions</a>
          </div>'''


def page(file, title, description, current, body):
    links = '\n'.join(f'        <a href="./{k}"{" aria-current=\"page\"" if k == current else ""}>{label}</a>' for k, label in NAV)
    html = f'''<!doctype html>
<html lang="en-IN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <meta name="description" content="{description}">
  <meta name="theme-color" content="#0a2540">
  <link rel="icon" href="./assets/mark.svg" type="image/svg+xml">
  <link rel="preload" href="./fonts/ibm-plex-serif-latin-600-normal.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="stylesheet" href="./assets/site.css">
  <script src="./assets/site-config.js" defer></script>
  <script src="./assets/site.js" defer></script>
</head>
<body>
  <a class="skip" href="#main">Skip to content</a>
  <div class="utility">
    <div class="wrap">
      <span data-site-optional>Appointments <a href="#" data-site="phone"></a></span>
      <span data-site-optional class="hide-small">WhatsApp <a href="#" data-site="whatsapp"></a></span>
      <span data-site-optional class="hide-small" data-site="hoursShort"></span>
      <a class="push" href="./staff/">Staff login</a>
    </div>
  </div>
  <header class="site-header">
    <div class="wrap">
      <a class="brand" href="./">{MARK}<span><span class="name">Vijaya Eye Clinic</span><span class="est"><b>Est. 1985</b><span class="tag"> &nbsp;Super-speciality eye care, Vijayanagar, Bengaluru</span></span></span></a>
      <button class="menu-button" type="button" aria-expanded="false" aria-controls="site-nav">Menu</button>
      <nav class="nav" id="site-nav" aria-label="Main">
{links}
        <a class="button" href="./contact" data-site="bookingUrl">Book appointment</a>
      </nav>
    </div>
  </header>
  <main id="main">
{body}
  </main>
  <footer class="site-footer">
    <div class="wrap">
      <div>
        <p class="fname">Vijaya Eye Clinic</p>
        <p>Super-speciality eye care in Vijayanagar, Bengaluru, since 1985.</p>
        <p data-site="address"></p>
      </div>
      <div>
        <h4>Care</h4>
        <ul>
          <li><a href="./specialities">Specialities</a></li>
          <li><a href="./doctors">Doctors</a></li>
          <li><a href="./specialities#facilities">Technology</a></li>
        </ul>
      </div>
      <div>
        <h4>Patients</h4>
        <ul>
          <li><a href="./patients">Your visit</a></li>
          <li><a href="./patients#insurance">Cashless Mediclaim</a></li>
          <li><a href="./patients#faq">Questions</a></li>
          <li><a href="./contact">Contact and directions</a></li>
        </ul>
      </div>
      <div>
        <h4>Clinic staff</h4>
        <ul>
          <li><a href="./staff/">Staff login</a></li>
        </ul>
      </div>
      <p class="legal">© <span id="year">2026</span> Vijaya Eye Clinic, Bengaluru. The information on this website is general and does not replace an examination by an eye doctor.</p>
    </div>
  </footer>
</body>
</html>
'''
    open(file, 'w').write(html)


def specialities_grid(link=True):
    out = []
    for slug, name, desc in SPECIALITIES:
        more = f'\n            <a href="./specialities#{slug}">About {name.lower()}</a>' if link else ''
        out.append(f'''          <div class="speciality">
            <h3>{name}</h3>
            <p>{desc}</p>{more}
          </div>''')
    return '\n'.join(out)


def doctors_grid():
    out = []
    for mono, name, role, quals, years, focus in DOCTORS:
        out.append(f'''          <article class="doctor">
            <div class="monogram" aria-hidden="true">{mono}</div>
            <h3>{name}</h3>
            <p class="role">{role}</p>
            <p class="quals">{quals}</p>
            <p class="focus">{focus}</p>
            <span class="years">{years} in practice</span>
          </article>''')
    return '\n'.join(out)


VISIT_STEPS = '''<ol class="steps">
          <li><h3>Call or message</h3><p>Call or WhatsApp to confirm the doctor's timings, or tell reception you are on your way. Walk-ins are welcome.</p></li>
          <li><h3>Registration and tests</h3><p>Register at the front desk; an optometrist checks your vision, glasses power and eye pressure.</p></li>
          <li><h3>Consultation</h3><p>A senior specialist examines your eyes with the slit lamp, and dilates the pupils when needed.</p></li>
          <li><h3>Your plan</h3><p>The doctor explains the diagnosis and treatment, your spectacle prescription, or surgery and cashless Mediclaim.</p></li>
        </ol>'''

# ------------------------------------------------------------------ home
page('index.html', 'Vijaya Eye Clinic | Super-speciality eye hospital in Vijayanagar, Bengaluru',
     'Vijaya Eye Clinic, Vijayanagar, Bengaluru: cataract, LASIK, retina, glaucoma and children\'s eye care by senior ophthalmologists since 1985. Cashless Mediclaim.',
     'home', f'''    <section class="hero" aria-labelledby="hero-title">
      <div class="wrap">
        <div>
          <h1 id="hero-title">Eye care for Bengaluru's families since 1985.</h1>
          <p class="lead">Senior ophthalmologists for cataract, LASIK, retina, glaucoma and children's eyes, with diagnostics and operation theatres under one roof in Vijayanagar.</p>
          <div class="actions">
            <a class="button" href="./contact" data-site="bookingUrl">Book an appointment</a>
            <a class="button quiet" href="./contact" data-site="phone" data-keep-text>Call the clinic</a>
          </div>
          <ul class="credentials">
            <li><strong>40+ years</strong><span>of eye care in Vijayanagar</span></li>
            <li><strong>4.9 on Google</strong><span>from more than 2,700 reviews</span></li>
            <li><strong>Cashless</strong><span>Mediclaim with 16 insurers and TPAs</span></li>
          </ul>
        </div>
        <figure class="chart" aria-label="A Snellen eye chart, from the letter E at 6/60 down to the 6/6 line">
          <ol aria-hidden="true">
            <li class="l1"><span>E</span><small>6/60</small></li>
            <li class="l2"><span>F P</span><small>6/36</small></li>
            <li class="l3"><span>T O Z</span><small>6/24</small></li>
            <li class="l4"><span>L P E D</span><small>6/18</small></li>
            <li class="l5"><span>P E C F D</span><small>6/12</small></li>
            <li class="l6"><span>E D F C Z P</span><small>6/6</small></li>
          </ol>
          <figcaption>6/6 is normal distance vision. Every consultation begins with a vision test.</figcaption>
        </figure>
      </div>
    </section>

    <section class="section" aria-labelledby="spec-title">
      <div class="wrap">
        <div class="section-head">
          <h2 id="spec-title">Specialities</h2>
          <p>Each patient is examined by a senior specialist after a complete work-up of vision and eye pressure.</p>
        </div>
        <div class="specialities">
{specialities_grid()}
        </div>
      </div>
    </section>

    <section class="section alt" aria-labelledby="doc-title">
      <div class="wrap">
        <div class="section-head">
          <h2 id="doc-title">Our doctors</h2>
          <a class="more" href="./doctors">All doctors and their areas of care</a>
        </div>
        <div class="doctors">
{doctors_grid()}
        </div>
      </div>
    </section>

    <section class="section" aria-labelledby="visit-title">
      <div class="wrap">
        <div class="section-head">
          <h2 id="visit-title">Your visit</h2>
          <p>Personal attention from senior consultants, explained clearly, without hurried referrals.</p>
        </div>
        {VISIT_STEPS}
      </div>
    </section>

    <section class="section alt" aria-labelledby="ins-title">
      <div class="wrap split">
        <div>
          <h2 id="ins-title">Cashless Mediclaim</h2>
          <p>Our Mediclaim desk prepares the documents and the pre-authorisation with your insurer or TPA, so that you can focus on your treatment.</p>
          <p><a href="./patients#insurance">What to bring for cashless treatment</a></p>
        </div>
        <ul class="insurers">
{chr(10).join(f"          <li>{i}</li>" for i in INSURERS)}
        </ul>
      </div>
    </section>

    <section class="section" aria-labelledby="visit-us-title">
      <div class="wrap contact-grid">
        <div>
          <h2 id="visit-us-title">Visit us</h2>
          {CONTACT_FACTS}
        </div>
        {URGENT}
      </div>
    </section>''')

# ------------------------------------------------------------------ specialities
detail = {
    'cataract': '''<p>A cataract is a clouding of the eye's natural lens, most often with age. Vision becomes dim or hazy, colours fade, and lights cause glare, especially at night.</p>
        <ul>
          <li><strong>Assessment:</strong> vision, a slit-lamp examination of the lens, and optical biometry to calculate the power of the lens implant.</li>
          <li><strong>Surgery:</strong> stitchless, blade-free micro-incision phacoemulsification (MICS) as day-care surgery. The cloudy lens is replaced with a clear intraocular lens.</li>
          <li><strong>Lens choices:</strong> monofocal lenses for clear distance vision, toric lenses that also correct astigmatism, and multifocal lenses for distance and near, from Zeiss and Alcon. The counsellor explains each option and its cost.</li>
          <li><strong>Recovery:</strong> eye drops for a few weeks, a review the next day, and new glasses once the eye has settled.</li>
        </ul>''',
    'lasik': '''<p>Laser vision correction reshapes the cornea to correct short sight, long sight and astigmatism, for adults whose glasses power has been stable.</p>
        <ul>
          <li><strong>Suitability tests:</strong> corneal topography, corneal thickness (pachymetry) and a dilated retina examination.</li>
          <li><strong>Contact lens wearers:</strong> stop wearing them for the period advised before the tests.</li>
          <li><strong>Procedure:</strong> a few minutes for each eye under numbing drops; vision usually improves within a day or two.</li>
        </ul>''',
    'retina': '''<p>The retina is the light-sensitive layer at the back of the eye. Many of its diseases need prompt, specialist care.</p>
        <ul>
          <li><strong>Diabetic retinopathy:</strong> diabetes can damage the retina's blood vessels without symptoms. Everyone with diabetes should have a dilated retina examination every year.</li>
          <li><strong>Macular degeneration:</strong> a blurred or distorted patch in the centre of vision, usually after 50.</li>
          <li><strong>Retinal tears and detachment:</strong> new floaters, flashes of light or a curtain over vision need to be examined the same day.</li>
          <li><strong>Diagnostics and treatment:</strong> OCT scans, fundus fluorescein angiography, retinal laser and vitreo-retinal surgery.</li>
        </ul>''',
    'glaucoma': '''<p>Glaucoma damages the optic nerve, usually because the pressure inside the eye is too high. Side vision is lost first, slowly and painlessly, which is why it is called the silent thief of sight. Lost vision cannot be restored, but further loss can usually be prevented.</p>
        <ul>
          <li><strong>Who should be checked:</strong> everyone over 40, and earlier with a family history of glaucoma, diabetes, high myopia or long-term steroid use.</li>
          <li><strong>Tests:</strong> eye pressure, examination of the optic nerve, automated visual fields and OCT.</li>
          <li><strong>Treatment:</strong> daily eye drops for most people, laser or surgery when needed, and regular reviews for life.</li>
        </ul>''',
    'children': '''<p>Children rarely complain about their eyesight. Early examination protects their vision for life.</p>
        <ul>
          <li><strong>Bring your child if they</strong> hold books close, squint or tilt their head, have a turning eye, rub their eyes often, or have headaches after school or screen time.</li>
          <li><strong>We assess</strong> refractive errors, lazy eye (amblyopia), squint and digital eye strain.</li>
          <li><strong>Treatment</strong> includes glasses, patching and exercises for amblyopia, and squint correction when needed.</li>
        </ul>''',
    'cornea': '''<p>A complete eye examination checks your vision, glasses power, eye pressure, and the health of the front and back of the eye.</p>
        <ul>
          <li><strong>Dry eye:</strong> tear-film assessment and treatment for gritty, burning or watering eyes.</li>
          <li><strong>Cornea:</strong> pterygium management and treatment of corneal infections.</li>
          <li><strong>Spectacles:</strong> your prescription after the doctor's examination, with in-house optical dispensing.</li>
        </ul>''',
}
spec_body = '\n'.join(f'''        <h2 id="{slug}">{name}</h2>
        {detail[slug]}''' for slug, name, _ in SPECIALITIES)
toc = ''.join(f'<a href="#{slug}">{name}</a>' for slug, name, _ in SPECIALITIES)
page('specialities.html', 'Specialities | Vijaya Eye Clinic, Bengaluru',
     'Cataract surgery, LASIK, retina and diabetic eye care, glaucoma, children\'s eye care and squint, cornea and dry eye at Vijaya Eye Clinic, Bengaluru.',
     'specialities', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Specialities</p>
        <h1>Specialities</h1>
        <p>What each condition is, the signs to watch for, and how it is diagnosed and treated. Your doctor will advise what is right for your eyes.</p>
        <nav class="toc" aria-label="On this page">{toc}<a href="#facilities">Technology</a></nav>
      </div>
    </div>
    <section class="section">
      <div class="wrap prose">
{spec_body}
      </div>
    </section>
    <section class="section alt" id="facilities" aria-labelledby="fac-title">
      <div class="wrap split">
        <div>
          <h2 id="fac-title">Technology and facilities</h2>
          <p>Diagnostics for accurate staging and surgical planning, and operation theatres run to NABH guidelines.</p>
        </div>
        <ul class="facility-list">
{chr(10).join(f"          <li><strong>{n}</strong><span>{d}</span></li>" for n, d in FACILITIES)}
        </ul>
      </div>
    </section>''')

# ------------------------------------------------------------------ doctors
page('doctors.html', 'Doctors | Vijaya Eye Clinic, Bengaluru',
     'The ophthalmologists of Vijaya Eye Clinic, Vijayanagar, Bengaluru: cataract, retina, glaucoma, cornea and refractive surgeons.',
     'doctors', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Doctors</p>
        <h1>Our doctors</h1>
        <p>Consultations are led by senior ophthalmologists. Call or message the clinic to confirm a doctor's timings before you come.</p>
      </div>
    </div>
    <section class="section">
      <div class="wrap">
        <div class="doctors">
{doctors_grid()}
        </div>
      </div>
    </section>''')

# ------------------------------------------------------------------ patients
faq = '\n'.join(f'''          <details>
            <summary>{q}</summary>
            <p>{a}</p>
          </details>''' for q, a in FAQ)
page('patients.html', 'Patients and visitors | Vijaya Eye Clinic, Bengaluru',
     'Your visit to Vijaya Eye Clinic: what to bring, the consultation, surgery day, cashless Mediclaim and common questions.',
     'patients', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Patients and visitors</p>
        <h1>Patients and visitors</h1>
        <p>What to bring, what happens at the clinic, surgery day, and cashless treatment.</p>
        <nav class="toc" aria-label="On this page"><a href="#visit">Your visit</a><a href="#bring">What to bring</a><a href="#surgery">Surgery day</a><a href="#insurance">Cashless Mediclaim</a><a href="#faq">Questions</a></nav>
      </div>
    </div>
    <section class="section" id="visit" aria-labelledby="v-title">
      <div class="wrap">
        <h2 id="v-title">Your visit</h2>
        {VISIT_STEPS}
      </div>
    </section>
    <section class="section alt">
      <div class="wrap split">
        <div class="prose">
          <h2 id="bring">What to bring</h2>
          <ul>
            <li>Your current glasses, and contact lens details if you wear them.</li>
            <li>Earlier eye reports and prescriptions, and your patient number if you have visited before.</li>
            <li>A list of the medicines you take, including eye drops, and any allergies.</li>
            <li>Someone to accompany you if your pupils may be dilated: vision stays blurred for a few hours.</li>
          </ul>
        </div>
        <div class="prose">
          <h2 id="surgery">Surgery day</h2>
          <ul>
            <li>Follow the fasting instructions you are given; take your usual medicines unless the doctor advised otherwise.</li>
            <li>Come with an adult who can take you home, without make-up around the eyes.</li>
            <li>Before surgery the team confirms your consent, marks the eye, checks blood pressure and sugar, and dilates the pupil.</li>
            <li>After surgery: use the drops as prescribed, wear the shield at night, do not rub the eye, and come for your review.</li>
          </ul>
        </div>
      </div>
    </section>
    <section class="section" id="insurance" aria-labelledby="i-title">
      <div class="wrap split">
        <div>
          <h2 id="i-title">Cashless Mediclaim</h2>
          <p>Tell reception or the counsellor at the start that you are insured. Our Mediclaim desk handles the documents and the pre-authorisation with your insurer or TPA.</p>
          <div class="panel">
            <h3>Bring for cashless processing</h3>
            <ul>
              <li>Original health insurance card or e-card</li>
              <li>Government photo ID (Aadhaar or PAN card)</li>
              <li>Past consultation papers and test reports</li>
              <li>Policy document with the policy number</li>
            </ul>
          </div>
        </div>
        <div>
          <h3>Insurers and TPAs</h3>
          <ul class="insurers">
{chr(10).join(f"            <li>{i}</li>" for i in INSURERS)}
          </ul>
          <p class="note">Coverage depends on your policy. Payment by cash, UPI and card is also accepted.</p>
        </div>
      </div>
    </section>
    <section class="section alt" id="faq" aria-labelledby="f-title">
      <div class="wrap split">
        <div>
          <h2 id="f-title">Questions</h2>
          <p>Anything else? Call or message the clinic.</p>
        </div>
        <div class="faq">
{faq}
        </div>
      </div>
    </section>''')

# ------------------------------------------------------------------ contact
page('contact.html', 'Contact and directions | Vijaya Eye Clinic, Bengaluru',
     'Address, phone, WhatsApp, timings and directions for Vijaya Eye Clinic, MC Layout, Vijayanagar, Bengaluru.',
     'contact', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Contact</p>
        <h1>Contact and directions</h1>
        <p>Five minutes from Vijayanagar Metro Station. BMTC buses from Majestic, Market and Rajajinagar stop in front of the clinic. Patients come from across West Bengaluru.</p>
      </div>
    </div>
    <section class="section">
      <div class="wrap contact-grid">
        <div>
          {CONTACT_FACTS}
        </div>
        {URGENT}
      </div>
    </section>
    <section class="section alt">
      <div class="wrap split">
        <div>
          <h2>Clinic staff</h2>
          <p>Doctors, optometrists, nurses, counsellors and office staff sign in to the clinic system here.</p>
        </div>
        <div><a class="button" href="./staff/">Staff login</a></div>
      </div>
    </section>''')

page('404.html', 'Page not found | Vijaya Eye Clinic', 'This page does not exist.', '', '''    <div class="page-head">
      <div class="wrap">
        <h1>This page does not exist</h1>
        <p>The link may be out of date. Go to the <a href="./">home page</a>, or use the menu.</p>
      </div>
    </div>''')
