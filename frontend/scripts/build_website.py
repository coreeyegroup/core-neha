# Generates the Vijaya Eye Clinic website pages (static HTML) into frontend/website/.
# Run: python3 frontend/scripts/build_website.py
# Content lists (doctors, specialities, insurers, FAQ) are at the top. Do not edit the HTML by hand.
# Doctor photos: put a JPEG at website/images/doctors/<slug>.jpg (portrait, about 800x500) and re-run.
import os
from html import escape

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'website')
os.chdir(ROOT)

MARK = ('<svg viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="22.5" fill="#0b2a4a"/>'
        '<path d="M8 24c4.2-6.6 9.6-10 16-10s11.8 3.4 16 10c-4.2 6.6-9.6 10-16 10S12.2 30.6 8 24z" fill="none" stroke="#fff" stroke-width="2.2" stroke-linejoin="round"/>'
        '<circle cx="24" cy="24" r="6.2" fill="#2fb3dd"/><circle cx="24" cy="24" r="2.6" fill="#04121f"/><circle cx="26.3" cy="21.7" r="1.2" fill="#fff"/></svg>')


def icon(paths):
    return f'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{paths}</svg>'


EYE = '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/>'
ICONS = {
    'cataract': icon(EYE + '<circle cx="12" cy="12" r="3.6"/><path d="M10.4 10.6a2 2 0 0 1 2-1"/>'),
    'lasik': icon('<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.4"/><path d="M12 2v4M12 18v4M2 12h4M18 12h4"/>'),
    'retina': icon('<circle cx="12" cy="12" r="9"/><circle cx="15.5" cy="12" r="1.6"/><path d="M14 12c-3 0-4.5-2-6.5-4.5M14 12c-3 0-4.5 2-6.5 4.5M10.5 9.6 7 11.2M10.5 14.4 7 12.8"/>'),
    'glaucoma': icon('<path d="M4.2 16.5a8.5 8.5 0 1 1 15.6 0"/><path d="m12 13.5 4-5"/><circle cx="12" cy="13.5" r="1.4"/><path d="M6.5 12h1M16.5 12h1M12 6.5v1"/>'),
    'children': icon('<circle cx="7" cy="14" r="3.6"/><circle cx="17" cy="14" r="3.6"/><path d="M10.6 13.4c.8-.6 2-.6 2.8 0M3.4 13 2 9.5M20.6 13 22 9.5"/>'),
    'cornea': icon('<path d="M12 3s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11z"/><path d="M9.5 14.5a2.6 2.6 0 0 0 2.5 2.5"/>'),
}
I_CLOCK = icon('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>')
I_PHONE = icon('<path d="M5 3h3l2 5-2.5 1.5a11 11 0 0 0 7 7L16 14l5 2v3a2 2 0 0 1-2 2A17 17 0 0 1 3 5a2 2 0 0 1 2-2z"/>')
I_CHAT = icon('<path d="M20 11.5a8 8 0 0 1-11.8 7L4 20l1.5-4A8 8 0 1 1 20 11.5z"/><path d="M9 10.5h6M9 13.5h4"/>')
I_PIN = icon('<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>')
I_LOCK = icon('<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>')
I_CAL = icon('<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>')
STAR = '<svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path d="m10 1.5 2.6 5.4 5.9.8-4.3 4.1 1 5.9L10 14.9l-5.2 2.8 1-5.9L1.5 7.7l5.9-.8z"/></svg>'

NAV = [('specialities', 'Specialities'), ('doctors', 'Doctors'), ('patients', 'Patients and visitors'), ('contact', 'Contact')]

DOCTORS = [
    ('vasant-patil', 'VP', 'Dr. Vasant N Patil', 'Founder and Chief Ophthalmologist', 'MBBS, DO, MS (Ophthalmology), FVR', '40+ years',
     'Comprehensive ophthalmology, cataract and glaucoma.'),
    ('vishwanath', 'V', 'Dr. Vishwanath', 'Senior Vitreo-Retinal Surgeon', 'MBBS, DO, MS (Ophthalmology), FVR', '25+ years',
     'Vitreo-retinal surgery, diabetic retinopathy and macular disorders.'),
    ('sunil-kumar', 'SK', 'Dr. Sunil Kumar M', 'Consultant Ophthalmologist and Phaco Surgeon', 'MBBS, DOMS, DNB', '15+ years',
     'Phacoemulsification cataract surgery and refractive care.'),
    ('savitha', 'SC', 'Dr. Savitha C S', 'Consultant Ophthalmologist', 'MS (Ophthalmology), FVR', '20+ years',
     'Anterior segment, medical retina and diagnostics.'),
    ('chandana', 'CK', 'Dr. Chandana K M', 'Consultant Ophthalmologist', 'MBBS, MS (Ophthalmology)', '10+ years',
     'Cornea care and eye examinations for children.'),
    ('dhananjaya', 'DK', 'Dr. Dhananjaya K H', 'Consultant Cataract, Cornea and Refractive Surgeon', 'MS (Ophthalmology), FPRS', '8+ years',
     'Phacoemulsification, cornea and LASIK.'),
]

SPECIALITIES = [
    ('cataract', 'Cataract surgery', 'Stitchless micro-incision phacoemulsification as day-care surgery, with monofocal, toric and multifocal lens choices.'),
    ('lasik', 'LASIK and refractive surgery', 'Laser vision correction planned from corneal topography and thickness, for adults who want to be free of glasses.'),
    ('retina', 'Retina and diabetic eye care', 'Diabetic retinopathy, macular degeneration and retinal detachment, with OCT, angiography and retinal laser.'),
    ('glaucoma', 'Glaucoma', 'Early detection with pressure, optic nerve and visual field tests, and careful lifelong management.'),
    ('children', 'Children\'s eye care and squint', 'Refractive errors, lazy eye (amblyopia), squint and digital eye strain, assessed in a child-friendly way.'),
    ('cornea', 'Cornea, dry eye and eye examinations', 'Complete eye examinations, dry eye care, pterygium and corneal infections, and in-house spectacles.'),
]

INSURERS = ['Star Health Insurance', 'HDFC ERGO', 'ICICI Lombard', 'Bajaj Allianz', 'Niva Bupa', 'Care Health Insurance',
            'United India Insurance', 'National Insurance', 'The Oriental Insurance', 'The New India Assurance', 'ManipalCigna',
            'Medi Assist TPA', 'Vidal Health TPA', 'Paramount Health', 'Heritage Health', 'Family Health Plan (FHPL)']

FACILITIES = [
    ('Micro-incision phacoemulsification', 'Cataract removal through an incision of about 2.2 mm, for a quicker recovery.'),
    ('High-definition OCT', 'Cross-sectional scans of the retina, macula and optic nerve.'),
    ('Fundus fluorescein angiography', 'Maps blood flow in the retina, and leakage from diabetic eye disease.'),
    ('Automated visual fields', 'Humphrey field analysis to detect early loss of side vision in glaucoma.'),
    ('Tonometry and pachymetry', 'Eye pressure and corneal thickness, measured without touching the eye.'),
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

DETAIL = {
    'cataract': ('A cataract is a clouding of the eye\'s natural lens, most often with age. Vision becomes dim or hazy, colours fade, and lights cause glare, especially at night.',
                 ['<strong>Assessment:</strong> vision, a slit-lamp examination of the lens, and optical biometry to calculate the power of the lens implant.',
                  '<strong>Surgery:</strong> stitchless, blade-free micro-incision phacoemulsification as day-care surgery. The cloudy lens is replaced with a clear intraocular lens.',
                  '<strong>Lens choices:</strong> monofocal lenses for clear distance vision, toric lenses that also correct astigmatism, and multifocal lenses for distance and near, from Zeiss and Alcon. The counsellor explains each option and its cost.',
                  '<strong>Recovery:</strong> eye drops for a few weeks, a review the next day, and new glasses once the eye has settled.'],
                 ['Optical biometry', 'Day-care phacoemulsification', 'Monofocal, toric and multifocal lenses', 'Cashless Mediclaim']),
    'lasik': ('Laser vision correction reshapes the cornea to correct short sight, long sight and astigmatism, for adults whose glasses power has been stable.',
              ['<strong>Suitability tests:</strong> corneal topography, corneal thickness (pachymetry) and a dilated retina examination.',
               '<strong>Contact lens wearers:</strong> stop wearing them for the period advised before the tests.',
               '<strong>Procedure:</strong> a few minutes for each eye under numbing drops; vision usually improves within a day or two.'],
              ['Corneal topography', 'Pachymetry', 'Dilated retina check', 'Same-day procedure']),
    'retina': ('The retina is the light-sensitive layer at the back of the eye. Many of its diseases need prompt, specialist care.',
               ['<strong>Diabetic retinopathy:</strong> diabetes can damage the retina\'s blood vessels without symptoms. Everyone with diabetes should have a dilated retina examination every year.',
                '<strong>Macular degeneration:</strong> a blurred or distorted patch in the centre of vision, usually after 50.',
                '<strong>Retinal tears and detachment:</strong> new floaters, flashes of light or a curtain over vision need to be examined the same day.',
                '<strong>Diagnostics and treatment:</strong> OCT scans, fundus fluorescein angiography, retinal laser and vitreo-retinal surgery.'],
               ['HD OCT', 'Fundus fluorescein angiography', 'Retinal laser', 'Vitreo-retinal surgery']),
    'glaucoma': ('Glaucoma damages the optic nerve, usually because the pressure inside the eye is too high. Side vision is lost first, slowly and painlessly. Lost vision cannot be restored, but further loss can usually be prevented.',
                 ['<strong>Who should be checked:</strong> everyone over 40, and earlier with a family history of glaucoma, diabetes, high myopia or long-term steroid use.',
                  '<strong>Tests:</strong> eye pressure, examination of the optic nerve, automated visual fields and OCT.',
                  '<strong>Treatment:</strong> daily eye drops for most people, laser or surgery when needed, and regular reviews for life.'],
                 ['Non-contact tonometry', 'Humphrey visual fields', 'Optic nerve OCT', 'Lifelong follow-up']),
    'children': ('Children rarely complain about their eyesight. Early examination protects their vision for life.',
                 ['<strong>Bring your child if they</strong> hold books close, squint or tilt their head, have a turning eye, rub their eyes often, or have headaches after school or screen time.',
                  '<strong>We assess</strong> refractive errors, lazy eye (amblyopia), squint and digital eye strain.',
                  '<strong>Treatment</strong> includes glasses, patching and exercises for amblyopia, and squint correction when needed.'],
                 ['Vision screening', 'Amblyopia therapy', 'Squint evaluation', 'Spectacles for children']),
    'cornea': ('A complete eye examination checks your vision, glasses power, eye pressure, and the health of the front and back of the eye.',
               ['<strong>Dry eye:</strong> tear-film assessment and treatment for gritty, burning or watering eyes.',
                '<strong>Cornea:</strong> pterygium management and treatment of corneal infections.',
                '<strong>Spectacles:</strong> your prescription after the doctor\'s examination, with in-house optical dispensing.'],
               ['Comprehensive examination', 'Dry eye assessment', 'Pterygium care', 'In-house optical']),
}

LINKS = {'cataract': 'About cataract surgery', 'lasik': 'About LASIK', 'retina': 'About retina care', 'glaucoma': 'About glaucoma',
         'children': 'About children\'s eye care', 'cornea': 'About eye examinations'}

URGENT = '''<aside class="urgent" aria-labelledby="urgent-title">
          <h3 id="urgent-title">When to seek help immediately</h3>
          <ul>
            <li>Sudden loss or dimming of vision in one or both eyes.</li>
            <li>A sudden shower of floaters, flashes of light, or a curtain over part of your vision.</li>
            <li>An injury to the eye, or a chemical splash: rinse with clean water for 15 minutes on the way.</li>
            <li>A painful red eye with headache, nausea or haloes around lights.</li>
          </ul>
          <p>Call the clinic, or go to the nearest emergency department.</p>
        </aside>'''

CONTACT_FACTS = '''<dl class="facts">
            <div class="row"><dt>Address</dt><dd data-site="address"></dd></div>
            <div class="row" data-site-optional><dt>Landmark</dt><dd data-site="landmark"></dd></div>
            <div class="row" data-site-optional><dt>Timings</dt><dd data-site="hours"></dd></div>
            <div class="row" data-site-optional><dt>Landline</dt><dd><a href="#" data-site="phone"></a></dd></div>
            <div class="row" data-site-optional><dt>Mobile and WhatsApp</dt><dd><a href="#" data-site="mobile"></a></dd></div>
            <div class="row" data-site-optional><dt>Email</dt><dd><a href="#" data-site="email"></a></dd></div>
          </dl>
          <div class="contact-actions">
            <a class="button" href="./contact" data-site="bookingUrl">''' + I_CAL + '''Book an appointment</a>
            <a class="button outline" href="#" data-site="whatsapp" data-keep-text>''' + I_CHAT + '''WhatsApp us</a>
            <a class="button outline" href="#" data-site="mapUrl">''' + I_PIN + '''Get directions</a>
          </div>'''

BAND = '''    <section class="band" aria-labelledby="band-title">
      <div class="wrap">
        <div>
          <h2 id="band-title">Book your eye examination</h2>
          <p>Monday to Saturday, 9:00 am to 9:30 pm. Walk-ins are welcome; booking ahead shortens your wait.</p>
        </div>
        <div class="actions">
          <a class="button light" href="./contact" data-site="bookingUrl">''' + I_CAL + '''Book an appointment</a>
          <a class="button ghost" href="./contact" data-site="phone" data-keep-text>''' + I_PHONE + '''Call the clinic</a>
        </div>
      </div>
    </section>'''


def page(file, title, description, current, body):
    links = '\n'.join(f'        <a href="./{k}"{" aria-current=\"page\"" if k == current else ""}>{label}</a>' for k, label in NAV)
    html = f'''<!doctype html>
<html lang="en-IN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <meta name="description" content="{description}">
  <meta name="theme-color" content="#071d35">
  <link rel="icon" href="./assets/mark.svg" type="image/svg+xml">
  <link rel="preload" href="./fonts/source-serif-4-latin-600-normal.woff2" as="font" type="font/woff2" crossorigin>
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
      <a class="push" href="./staff/">{I_LOCK}Staff login</a>
    </div>
  </div>
  <header class="site-header">
    <div class="wrap">
      <a class="brand" href="./">{MARK}<span><span class="name">Vijaya Eye Clinic</span><span class="tag">Super-speciality eye hospital, Vijayanagar, Bengaluru. Since 1985.</span></span></a>
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
        <p class="fbrand">{MARK}Vijaya Eye Clinic</p>
        <p>Super-speciality eye care in Vijayanagar, Bengaluru, since 1985.</p>
        <p data-site="address"></p>
      </div>
      <div>
        <h4>Care</h4>
        <ul>
          <li><a href="./specialities">Specialities</a></li>
          <li><a href="./doctors">Doctors</a></li>
          <li><a href="./specialities#technology">Technology</a></li>
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
        <h4>Contact</h4>
        <ul>
          <li data-site-optional><a href="#" data-site="phone"></a></li>
          <li data-site-optional><a href="#" data-site="whatsapp" data-keep-text>WhatsApp</a></li>
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


def specialities_grid():
    return '\n'.join(f'''          <article class="speciality">
            <div class="icon">{ICONS[slug]}</div>
            <h3>{name}</h3>
            <p>{desc}</p>
            <a href="./specialities#{slug}">{LINKS[slug]}</a>
          </article>''' for slug, name, desc in SPECIALITIES)


def doctors_grid():
    out = []
    for slug, mono, name, role, quals, years, focus in DOCTORS:
        photo = f'images/doctors/{slug}.jpg'
        img = f'<img src="./{photo}" alt="{escape(name)}" loading="lazy">' if os.path.exists(photo) else f'<span class="monogram" aria-hidden="true">{mono}</span>'
        out.append(f'''          <article class="doctor">
            <div class="portrait">{img}</div>
            <div class="body">
              <h3>{name}</h3>
              <p class="role">{role}</p>
              <p class="quals">{quals}</p>
              <p class="focus">{focus}</p>
              <span class="years">{years} in practice</span>
            </div>
          </article>''')
    return '\n'.join(out)


def tech_list():
    return '\n'.join(f'          <li><strong>{n}</strong><span>{d}</span></li>' for n, d in FACILITIES)


def insurers(indent='          '):
    return '\n'.join(f'{indent}<li>{i}</li>' for i in INSURERS)


VISIT_STEPS = '''<ol class="steps">
          <li><h3>Call or message</h3><p>Call or WhatsApp to confirm the doctor's timings, or tell reception you are on your way. Walk-ins are welcome.</p></li>
          <li><h3>Registration and tests</h3><p>Register at the front desk; an optometrist checks your vision, glasses power and eye pressure.</p></li>
          <li><h3>Consultation</h3><p>A senior specialist examines your eyes with the slit lamp, and dilates the pupils when needed.</p></li>
          <li><h3>Your plan</h3><p>The doctor explains the diagnosis and treatment, your spectacle prescription, or surgery and cashless Mediclaim.</p></li>
        </ol>'''

CHECKLIST = '''<ul class="checklist">
            <li>Original health insurance card or e-card</li>
            <li>Government photo ID (Aadhaar or PAN card)</li>
            <li>Past consultation papers and test reports</li>
            <li>Policy document with the policy number</li>
          </ul>'''

# ------------------------------------------------------------------ home
page('index.html', 'Vijaya Eye Clinic | Super-speciality eye hospital in Vijayanagar, Bengaluru',
     'Vijaya Eye Clinic, Vijayanagar, Bengaluru: cataract, LASIK, retina, glaucoma and children\'s eye care by senior ophthalmologists since 1985. Cashless Mediclaim.',
     'home', f'''    <section class="hero" aria-labelledby="hero-title">
      <img class="art" src="./assets/iris.svg" alt="">
      <div class="wrap">
        <div>
          <p class="place">Vijayanagar, Bengaluru. Caring for eyes since 1985.</p>
          <h1 id="hero-title">Clear sight, in the care of senior specialists.</h1>
          <p class="lead">Cataract, LASIK, retina, glaucoma and children's eye care, with advanced diagnostics and modular operation theatres under one roof.</p>
          <div class="actions">
            <a class="button" href="./contact" data-site="bookingUrl">{I_CAL}Book an appointment</a>
            <a class="button ghost" href="./contact" data-site="phone" data-keep-text>{I_PHONE}Call the clinic</a>
          </div>
        </div>
        <aside class="desk" aria-labelledby="desk-title">
          <header>
            <h2 id="desk-title">Appointment desk</h2>
            <p>Walk-ins welcome. Call ahead to confirm the doctor's timings.</p>
          </header>
          <dl>
            <div class="row" data-site-optional>{I_CLOCK}<div><dt>Timings</dt><dd data-site="hours"></dd></div></div>
            <div class="row" data-site-optional>{I_PHONE}<div><dt>Appointments</dt><dd><a href="#" data-site="phone"></a></dd></div></div>
            <div class="row" data-site-optional>{I_CHAT}<div><dt>Mobile and WhatsApp</dt><dd><a href="#" data-site="mobile"></a></dd></div></div>
            <div class="row">{I_PIN}<div><dt>Address</dt><dd data-site="address"></dd></div></div>
          </dl>
          <div class="desk-actions">
            <a class="button" href="./contact" data-site="bookingUrl">Book online</a>
            <a class="button outline" href="#" data-site="mapUrl">Directions</a>
          </div>
        </aside>
      </div>
    </section>

    <section class="trust" aria-label="About the clinic">
      <div class="wrap">
        <ul>
          <li><strong>Since 1985</strong><span>Four decades of eye care in West Bengaluru</span></li>
          <li><strong>4.9<span class="stars">{STAR * 5}</span></strong><span>Google rating from more than 2,700 reviews</span></li>
          <li><strong>6 specialists</strong><span>Cataract, retina, cornea, glaucoma and refractive surgeons</span></li>
          <li><strong>Cashless</strong><span>Mediclaim with 16 insurers and TPAs</span></li>
        </ul>
      </div>
    </section>

    <section class="section" aria-labelledby="spec-title">
      <div class="wrap">
        <div class="section-head">
          <div><span class="rule"></span><h2 id="spec-title">Specialities</h2></div>
          <p>Every patient is examined by a senior specialist after a complete work-up of vision and eye pressure.</p>
        </div>
        <div class="specialities">
{specialities_grid()}
        </div>
      </div>
    </section>

    <section class="section dark" aria-labelledby="tech-title">
      <div class="wrap tech">
        <div class="intro">
          <span class="rule"></span>
          <h2 id="tech-title">Diagnostics and surgery under one roof</h2>
          <p>Accurate diagnosis comes first. Our imaging and testing equipment lets the doctor stage a condition precisely and plan surgery with confidence.</p>
          <p class="note"><strong>Operation theatres</strong> are run to NABH guidelines, with HEPA filtration and autoclave sterilisation.</p>
        </div>
        <ul>
{tech_list()}
        </ul>
      </div>
    </section>

    <section class="section" aria-labelledby="doc-title">
      <div class="wrap">
        <div class="section-head">
          <div><span class="rule"></span><h2 id="doc-title">Our doctors</h2></div>
          <a class="more" href="./doctors">All doctors and their areas of care</a>
        </div>
        <div class="doctors">
{doctors_grid()}
        </div>
      </div>
    </section>

    <section class="section tint" aria-labelledby="visit-title">
      <div class="wrap">
        <div class="section-head">
          <div><span class="rule"></span><h2 id="visit-title">Your visit</h2></div>
          <p>Personal attention from senior consultants, explained clearly, without hurried referrals.</p>
        </div>
        {VISIT_STEPS}
      </div>
    </section>

    <section class="section" aria-labelledby="ins-title">
      <div class="wrap insurance">
        <div>
          <span class="rule"></span>
          <h2 id="ins-title">Cashless Mediclaim</h2>
          <p class="lead">Our Mediclaim desk prepares the documents and the pre-authorisation with your insurer or TPA, so that you can focus on your treatment. Bring:</p>
          {CHECKLIST}
        </div>
        <div>
          <ul class="insurers">
{insurers()}
          </ul>
          <p class="insurers-note">Coverage depends on your policy. Cash, UPI and cards are also accepted.</p>
        </div>
      </div>
    </section>

{BAND}

    <section class="section" aria-labelledby="visit-us-title">
      <div class="wrap contact-grid">
        <div>
          <span class="rule"></span>
          <h2 id="visit-us-title">Visit us</h2>
          {CONTACT_FACTS}
        </div>
        {URGENT}
      </div>
    </section>''')

# ------------------------------------------------------------------ specialities
topics = []
for slug, name, _ in SPECIALITIES:
    intro, points, glance = DETAIL[slug]
    pts = '\n'.join(f'              <li>{p}</li>' for p in points)
    gl = '\n'.join(f'              <li>{g}</li>' for g in glance)
    topics.append(f'''        <article class="topic" id="{slug}">
          <div class="icon">{ICONS[slug]}</div>
          <div>
            <h2>{name}</h2>
            <div class="topic-body">
              <div>
                <p>{intro}</p>
                <ul>
{pts}
                </ul>
              </div>
              <aside class="glance">
                <h3>At a glance</h3>
                <ul>
{gl}
                </ul>
              </aside>
            </div>
          </div>
        </article>''')
toc = ''.join(f'<a href="#{slug}">{name}</a>' for slug, name, _ in SPECIALITIES)
page('specialities.html', 'Specialities | Vijaya Eye Clinic, Bengaluru',
     'Cataract surgery, LASIK, retina and diabetic eye care, glaucoma, children\'s eye care and squint, cornea and dry eye at Vijaya Eye Clinic, Bengaluru.',
     'specialities', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Specialities</p>
        <h1>Specialities</h1>
        <p>What each condition is, the signs to watch for, and how it is diagnosed and treated. Your doctor will advise what is right for your eyes.</p>
        <nav class="toc" aria-label="On this page">{toc}<a href="#technology">Technology</a></nav>
      </div>
    </div>
    <section class="section">
      <div class="wrap">
{chr(10).join(topics)}
      </div>
    </section>
    <section class="section dark" id="technology" aria-labelledby="tech-title">
      <div class="wrap tech">
        <div class="intro">
          <span class="rule"></span>
          <h2 id="tech-title">Technology and facilities</h2>
          <p>Diagnostics for accurate staging and surgical planning, and operation theatres run to NABH guidelines.</p>
        </div>
        <ul>
{tech_list()}
        </ul>
      </div>
    </section>
{BAND}''')

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
    </section>
{BAND}''')

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
        <div class="section-head">
          <div><span class="rule"></span><h2 id="v-title">Your visit</h2></div>
          <p>Allow one to two hours. If your pupils are dilated, bring someone to accompany you.</p>
        </div>
        {VISIT_STEPS}
      </div>
    </section>
    <section class="section tint">
      <div class="wrap two-col">
        <div class="prose" id="bring">
          <span class="rule"></span>
          <h2>What to bring</h2>
          <ul>
            <li>Your current glasses, and contact lens details if you wear them.</li>
            <li>Earlier eye reports and prescriptions, and your patient number if you have visited before.</li>
            <li>A list of the medicines you take, including eye drops, and any allergies.</li>
            <li>Someone to accompany you if your pupils may be dilated: vision stays blurred for a few hours.</li>
          </ul>
        </div>
        <div class="prose" id="surgery">
          <span class="rule"></span>
          <h2>Surgery day</h2>
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
      <div class="wrap insurance">
        <div>
          <span class="rule"></span>
          <h2 id="i-title">Cashless Mediclaim</h2>
          <p class="lead">Tell reception or the counsellor at the start that you are insured. Our Mediclaim desk handles the documents and the pre-authorisation with your insurer or TPA. Bring:</p>
          {CHECKLIST}
        </div>
        <div>
          <ul class="insurers">
{insurers()}
          </ul>
          <p class="insurers-note">Coverage depends on your policy. Cash, UPI and cards are also accepted.</p>
        </div>
      </div>
    </section>
    <section class="section tint" id="faq" aria-labelledby="f-title">
      <div class="wrap insurance">
        <div>
          <span class="rule"></span>
          <h2 id="f-title">Questions</h2>
          <p class="lead">Anything else? Call or message the clinic.</p>
        </div>
        <div class="faq">
{faq}
        </div>
      </div>
    </section>
{BAND}''')

# ------------------------------------------------------------------ contact
page('contact.html', 'Contact and directions | Vijaya Eye Clinic, Bengaluru',
     'Address, phone, WhatsApp, timings and directions for Vijaya Eye Clinic, MC Layout, Vijayanagar, Bengaluru.',
     'contact', f'''    <div class="page-head">
      <div class="wrap">
        <p class="crumbs"><a href="./">Home</a> / Contact</p>
        <h1>Contact and directions</h1>
        <p>Five minutes from Vijayanagar Metro Station. BMTC buses from Majestic, Market and Rajajinagar stop in front of the clinic.</p>
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
    <section class="section tint">
      <div class="wrap">
        <div class="staff-box">
          <div>
            <h2>Clinic staff</h2>
            <p>Doctors, optometrists, nurses, counsellors and office staff sign in to the clinic system here.</p>
          </div>
          <a class="button" href="./staff/">{I_LOCK}Staff login</a>
        </div>
      </div>
    </section>''')

page('404.html', 'Page not found | Vijaya Eye Clinic', 'This page does not exist.', '', '''    <div class="page-head">
      <div class="wrap">
        <h1>This page does not exist</h1>
        <p>The link may be out of date. Go to the <a href="./">home page</a>, or use the menu.</p>
      </div>
    </div>''')
