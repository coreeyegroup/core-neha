/**
 * The knowledge centre: the operation manual (for hospital staff and management) and the
 * developer guide. Chapters are Markdown files in src/help/manual and src/help/developer,
 * named NN-slug.md; the first line is "# Title" and the next "> one-line summary".
 * They are bundled into the staff application, so the help always matches the release.
 */
export type BookId = 'manual' | 'developer';

export interface Chapter {
  book: BookId;
  slug: string;
  order: number;
  title: string;
  summary: string;
  body: string;
  sections: Array<{ id: string; title: string; text: string }>;
}

export const BOOKS: Record<BookId, { title: string; description: string }> = {
  manual: {
    title: 'Operation manual',
    description: 'How to run the system: every screen, setting and daily task, for staff and management.',
  },
  developer: {
    title: 'Developer guide',
    description: 'Architecture, code structure, data model, rules, flows, testing and operations, for engineers.',
  },
};

export function slugify(text: string): string {
  return text.toLowerCase().replace(/[`*_[\]()]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function parse(book: BookId, path: string, raw: string): Chapter {
  const file = path.split('/').pop()!.replace(/\.md$/, '');
  const m = /^(\d+)-(.+)$/.exec(file);
  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  const title = (lines[0] ?? '').replace(/^#\s+/, '').trim();
  const summary = (lines[1] ?? '').startsWith('>') ? (lines[1] ?? '').replace(/^>\s*/, '').trim() : '';
  const body = lines.slice(summary ? 2 : 1).join('\n').trim();
  const sections: Chapter['sections'] = [];
  let current = { id: '', title, text: '' };
  for (const line of body.split('\n')) {
    const h = /^##\s+(.+)$/.exec(line);
    if (h) {
      sections.push(current);
      current = { id: slugify(h[1]!), title: h[1]!, text: '' };
    } else {
      current.text += `${line}\n`;
    }
  }
  sections.push(current);
  return { book, slug: m ? m[2]! : file, order: m ? Number(m[1]) : 99, title, summary, body, sections };
}

const manualFiles = import.meta.glob('./manual/*.md', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
const developerFiles = import.meta.glob('./developer/*.md', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

export const CHAPTERS: Chapter[] = [
  ...Object.entries(manualFiles).map(([p, raw]) => parse('manual', p, raw)),
  ...Object.entries(developerFiles).map(([p, raw]) => parse('developer', p, raw)),
].sort((a, b) => (a.book === b.book ? a.order - b.order : a.book === 'manual' ? -1 : 1));

export function chaptersOf(book: BookId): Chapter[] {
  return CHAPTERS.filter((c) => c.book === book);
}

export function findChapter(book: string | undefined, slug: string | undefined): Chapter | undefined {
  return CHAPTERS.find((c) => c.book === book && c.slug === slug);
}

export interface SearchHit {
  chapter: Chapter;
  sectionId: string;
  sectionTitle: string;
  snippet: string;
  score: number;
}

/** Plain word search over every section; every word must appear. */
export function search(query: string, limit = 40): SearchHit[] {
  const words = query.toLowerCase().split(/\s+/).filter((w) => w.length > 1);
  if (words.length === 0) {
    return [];
  }
  const hits: SearchHit[] = [];
  for (const chapter of CHAPTERS) {
    for (const s of chapter.sections) {
      const hay = `${s.title}\n${s.text}`.toLowerCase();
      if (!words.every((w) => hay.includes(w))) {
        continue;
      }
      const score = words.reduce((n, w) => n + (s.title.toLowerCase().includes(w) ? 5 : 0) + (chapter.title.toLowerCase().includes(w) ? 2 : 0), 1);
      const at = hay.indexOf(words[0]!);
      const plain = s.text.replace(/[#*`|>]/g, ' ').replace(/\s+/g, ' ');
      const pos = Math.max(0, plain.toLowerCase().indexOf(words[0]!) - 60);
      hits.push({ chapter, sectionId: s.id, sectionTitle: s.title, snippet: `${pos > 0 ? '…' : ''}${plain.slice(pos, pos + 180)}…`, score: score - at / 100000 });
    }
  }
  return hits.sort((a, b) => b.score - a.score).slice(0, limit);
}

/** The manual chapter that explains the current screen (for the help button in the top bar). */
export function chapterForPath(pathname: string): string {
  const rules: Array<[RegExp, string]> = [
    [/^\/reception/, '/help/manual/reception'],
    [/^\/optometry/, '/help/manual/optometry'],
    [/^\/consultation/, '/help/manual/consultation'],
    [/^\/counselling/, '/help/manual/surgery-counselling'],
    [/^\/ot/, '/help/manual/operation-theatre'],
    [/^\/surgeries/, '/help/manual/operation-theatre'],
    [/^\/accounts/, '/help/manual/accounts'],
    [/^\/bills/, '/help/manual/accounts'],
    [/^\/visits/, '/help/manual/the-visit-record'],
    [/^\/patients/, '/help/manual/patient-database'],
    [/^\/(organisation|centres|departments)/, '/help/manual/organisation-and-centres'],
    [/^\/(positions|people)/, '/help/manual/people-and-positions'],
    [/^\/(users|roles|approvals)/, '/help/manual/users-roles-and-access'],
    [/^\/audit/, '/help/manual/audit-privacy-and-security'],
  ];
  return rules.find(([r]) => r.test(pathname))?.[1] ?? '/help';
}
