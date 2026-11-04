import { Alert, Box, Link, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';

import { slugify } from './content';

/**
 * A small Markdown renderer for the help chapters: headings, paragraphs, nested lists,
 * tables, code blocks, call-outs (> **Note** / **Warning** / **Tip**), bold, italic,
 * inline code and links. It builds React elements; no HTML from the text is injected.
 */
export function Markdown({ source }: { source: string }) {
  return <Box sx={{ '& > *:first-of-type': { mt: 0 } }}>{blocks(source.replace(/\r\n/g, '\n').split('\n'))}</Box>;
}

function inline(text: string, key = 'i'): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\s][^*]*\*)|(\[[^\]]+\]\([^)\s]+\))/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let n = 0;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) {
      out.push(text.slice(last, m.index));
    }
    const t = m[0];
    const k = `${key}-${n++}`;
    if (m[1]) {
      out.push(<Box key={k} component="code" sx={{ fontFamily: 'monospace', fontSize: '0.92em', bgcolor: 'action.hover', px: 0.5, borderRadius: 0.5 }}>{t.slice(1, -1)}</Box>);
    } else if (m[2]) {
      out.push(<strong key={k}>{inline(t.slice(2, -2), k)}</strong>);
    } else if (m[3]) {
      out.push(<em key={k}>{inline(t.slice(1, -1), k)}</em>);
    } else {
      const lm = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(t)!;
      const href = lm[2]!;
      out.push(href.startsWith('/') || href.startsWith('#')
        ? <Link key={k} component={RouterLink} to={href}>{inline(lm[1]!, k)}</Link>
        : <Link key={k} href={href} target="_blank" rel="noreferrer">{inline(lm[1]!, k)}</Link>);
    }
    last = m.index + t.length;
  }
  if (last < text.length) {
    out.push(text.slice(last));
  }
  return out;
}

const LIST = /^(\s*)([-*]|\d+\.)\s+(.*)$/;

interface Item { text: string; children: Item[]; ordered: boolean }

function list(lines: string[], start: number): { node: ReactNode; next: number } {
  let i = start;
  // A list runs over list lines and indented continuation lines; a blank line followed by a list line continues it.
  while (i < lines.length) {
    const line = lines[i]!;
    if (LIST.test(line) || (/^\s{2,}\S/.test(line) && i > start)) {
      i++;
    } else if (line.trim() === '' && i + 1 < lines.length && LIST.test(lines[i + 1]!)) {
      i++;
    } else {
      break;
    }
  }
  const render = (items: Item[], key: string): ReactNode => {
    const ordered = items[0]?.ordered;
    return (
      <Box key={key} component={ordered ? 'ol' : 'ul'} sx={{ pl: 3, my: 1, '& li': { mb: 0.5 } }}>
        {items.map((it, j) => (
          <Typography key={j} component="li" variant="body1">
            {inline(it.text, `${key}-${j}`)}
            {it.children.length > 0 && render(it.children, `${key}-${j}c`)}
          </Typography>
        ))}
      </Box>
    );
  };
  return { node: render(rebuild(lines.slice(start, i).filter((l) => l.trim() !== '')), `l${start}`), next: i };
}

/** Builds the item tree by indentation (2 or more spaces = one level deeper). */
function rebuild(lines: string[]): Item[] {
  const root: Item[] = [];
  const path: Array<{ indent: number; item: Item }> = [];
  for (const line of lines) {
    const m = LIST.exec(line);
    if (!m) {
      const last = path[path.length - 1];
      if (last) {
        last.item.text += ` ${line.trim()}`;
      }
      continue;
    }
    const indent = m[1]!.length;
    const item: Item = { text: m[3]!, children: [], ordered: /\d/.test(m[2]!) };
    while (path.length > 0 && path[path.length - 1]!.indent >= indent) {
      path.pop();
    }
    if (path.length === 0) {
      root.push(item);
    } else {
      path[path.length - 1]!.item.children.push(item);
    }
    path.push({ indent, item });
  }
  return root;
}

function cells(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
}

function blocks(lines: string[]): ReactNode[] {
  const out: ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i]!;
    if (line.trim() === '') {
      i++;
      continue;
    }
    if (line.startsWith('```')) {
      const code: string[] = [];
      i++;
      while (i < lines.length && !lines[i]!.startsWith('```')) {
        code.push(lines[i]!);
        i++;
      }
      i++;
      out.push(
        <Box key={`c${i}`} component="pre" sx={{ fontFamily: 'monospace', fontSize: 13, bgcolor: 'action.hover', p: 1.5, borderRadius: 1, overflowX: 'auto', my: 1.5 }}>
          {code.join('\n')}
        </Box>,
      );
      continue;
    }
    const h = /^(#{1,4})\s+(.+)$/.exec(line);
    if (h) {
      const level = h[1]!.length;
      const text = h[2]!;
      out.push(
        <Typography key={`h${i}`} id={slugify(text)} variant={level <= 2 ? 'h2' : 'h3'} component={`h${Math.min(level + 1, 6)}` as 'h2'}
          sx={{ mt: level <= 2 ? 4 : 2.5, mb: 1, scrollMarginTop: 72 }}>
          {inline(text)}
        </Typography>,
      );
      i++;
      continue;
    }
    if (/^---+\s*$/.test(line)) {
      out.push(<Box key={`r${i}`} component="hr" sx={{ border: 0, borderTop: 1, borderColor: 'divider', my: 3 }} />);
      i++;
      continue;
    }
    if (line.trim().startsWith('|')) {
      const rows: string[] = [];
      while (i < lines.length && lines[i]!.trim().startsWith('|')) {
        rows.push(lines[i]!);
        i++;
      }
      const head = cells(rows[0]!);
      const body = rows.slice(/^\s*\|?\s*:?-{2,}/.test(rows[1] ?? '') ? 2 : 1).map(cells);
      out.push(
        <TableContainer key={`t${i}`} sx={{ my: 1.5, border: 1, borderColor: 'divider', borderRadius: 1 }}>
          <Table size="small">
            <TableHead><TableRow>{head.map((c, j) => <TableCell key={j} sx={{ fontWeight: 600 }}>{inline(c, `th${i}-${j}`)}</TableCell>)}</TableRow></TableHead>
            <TableBody>
              {body.map((r, k) => (
                <TableRow key={k}>{head.map((_, j) => <TableCell key={j} sx={{ verticalAlign: 'top' }}>{inline(r[j] ?? '', `td${i}-${k}-${j}`)}</TableCell>)}</TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>,
      );
      continue;
    }
    if (line.startsWith('>')) {
      const quote: string[] = [];
      while (i < lines.length && lines[i]!.startsWith('>')) {
        quote.push(lines[i]!.replace(/^>\s?/, ''));
        i++;
      }
      const first = quote[0] ?? '';
      const severity = /^\*\*(Warning|Important|Do not)/i.test(first) ? 'warning' : /^\*\*Tip/i.test(first) ? 'success' : 'info';
      out.push(<Alert key={`q${i}`} severity={severity} sx={{ my: 1.5, '& p': { m: 0 } }}>{blocks(quote)}</Alert>);
      continue;
    }
    if (LIST.test(line)) {
      const { node, next } = list(lines, i);
      out.push(node);
      i = next;
      continue;
    }
    const para: string[] = [];
    while (i < lines.length && lines[i]!.trim() !== '' && !/^(#{1,4}\s|```|>|\s*\||---)/.test(lines[i]!) && !LIST.test(lines[i]!)) {
      para.push(lines[i]!.trim());
      i++;
    }
    out.push(<Typography key={`p${i}`} variant="body1" component="p" sx={{ my: 1.25, lineHeight: 1.65 }}>{inline(para.join(' '), `p${i}`)}</Typography>);
  }
  return out;
}
