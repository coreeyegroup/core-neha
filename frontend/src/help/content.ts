/**
 * The knowledge centre. Chapters come from the server (GET /v1/help), which sends each user
 * only the chapters for the work their roles allow; the developer guide only to group-wide
 * administrators. Sources: backend/src/main/resources/help/{manual,developer}/NN-slug.md.
 */
import { useQuery } from '@tanstack/react-query';

import { helpLibrary, type HelpLibrary } from '../api/generated/core-neha';
import type { ApiError } from '../api/http';

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
    description: 'How to do your work in the system: the screens, options and daily tasks of your position.',
  },
  developer: {
    title: 'Developer guide',
    description: 'Architecture, code, data model, rules, flows, testing and operations. For administrators and engineers.',
  },
};

export function slugify(text: string): string {
  return text.toLowerCase().replace(/[`*_[\]()]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function toChapter(c: NonNullable<HelpLibrary['chapters']>[number]): Chapter {
  const title = c.title ?? '';
  const body = c.body ?? '';
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
  return { book: (c.book as BookId) ?? 'manual', slug: c.slug ?? '', order: c.order ?? 99, title, summary: c.summary ?? '', body, sections };
}

export interface Help {
  administrator: boolean;
  chapters: Chapter[];
}

export function useHelp() {
  return useQuery<Help, ApiError>({
    queryKey: ['help'],
    queryFn: async () => {
      const lib = await helpLibrary();
      return { administrator: !!lib.administrator, chapters: (lib.chapters ?? []).map(toChapter) };
    },
    staleTime: 10 * 60_000,
  });
}

export function chaptersOf(chapters: Chapter[], book: BookId): Chapter[] {
  return chapters.filter((c) => c.book === book);
}

export interface SearchHit {
  chapter: Chapter;
  sectionId: string;
  sectionTitle: string;
  snippet: string;
  score: number;
}

/** Plain word search over every section the user may read; every word must appear. */
export function search(chapters: Chapter[], query: string, limit = 40): SearchHit[] {
  const words = query.toLowerCase().split(/\s+/).filter((w) => w.length > 1);
  if (words.length === 0) {
    return [];
  }
  const hits: SearchHit[] = [];
  for (const chapter of chapters) {
    for (const s of chapter.sections) {
      const hay = `${s.title}\n${s.text}`.toLowerCase();
      if (!words.every((w) => hay.includes(w))) {
        continue;
      }
      const score = words.reduce((n, w) => n + (s.title.toLowerCase().includes(w) ? 5 : 0) + (chapter.title.toLowerCase().includes(w) ? 2 : 0), 1);
      const plain = s.text.replace(/[#*`|>]/g, ' ').replace(/\s+/g, ' ');
      const pos = Math.max(0, plain.toLowerCase().indexOf(words[0]!) - 60);
      hits.push({ chapter, sectionId: s.id, sectionTitle: s.title, snippet: `${pos > 0 ? '…' : ''}${plain.slice(pos, pos + 180)}…`, score });
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
