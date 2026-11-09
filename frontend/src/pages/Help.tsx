import { Alert, Box, Button, Chip, Divider, Link, List, ListItemButton, ListItemText, ListSubheader, Paper, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useLocation, useParams } from 'react-router';

import { useSession } from '../auth/session';
import { PageHeader } from '../components/PageHeader';
import { ProblemAlert } from '../components/ProblemAlert';
import { BOOKS, chaptersOf, search, useHelp, type BookId, type Chapter } from '../help/content';
import { Markdown } from '../help/Markdown';

function SearchBox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <Box>
      <TextField value={value} onChange={(e) => onChange(e.target.value)} placeholder="Search your help: e.g. check in, discount, day close"
        slotProps={{ htmlInput: { 'aria-label': 'Search the help' } }} sx={{ width: { xs: '100%', md: 560 }, mb: 2 }} />
    </Box>
  );
}

function Results({ chapters, query }: { chapters: Chapter[]; query: string }) {
  const hits = useMemo(() => search(chapters, query), [chapters, query]);
  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Typography variant="h3" gutterBottom>{hits.length === 0 ? 'Nothing found' : `${hits.length} result${hits.length === 1 ? '' : 's'}`}</Typography>
      {hits.length === 0 && <Typography variant="body2" color="text.secondary">Try fewer or different words.</Typography>}
      <Stack spacing={1.5} divider={<Divider flexItem />}>
        {hits.map((h, i) => (
          <Box key={i}>
            <Link component={RouterLink} to={`/help/${h.chapter.book}/${h.chapter.slug}${h.sectionId ? `#${h.sectionId}` : ''}`} sx={{ fontWeight: 600 }}>
              {h.chapter.title}{h.sectionId ? ` › ${h.sectionTitle}` : ''}
            </Link>
            <Typography variant="caption" color="text.secondary" component="div">{BOOKS[h.chapter.book].title}</Typography>
            <Typography variant="body2" color="text.secondary">{h.snippet}</Typography>
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}

/** What the signed-in person does in the system: positions, roles and responsibilities. */
function YourWork() {
  const { me } = useSession();
  const postings = me.postings ?? [];
  const roles = (me.roles ?? []).filter((r) => r.kind === 'ACCESS');
  const responsibilities = me.responsibilities ?? [];
  return (
    <Paper sx={{ p: 2, mb: 3 }}>
      <Typography variant="h3" gutterBottom>Your work in the system</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>Positions</Typography>
          {postings.length === 0 && <Typography variant="body2">None recorded.</Typography>}
          {postings.map((p, i) => <Typography key={i} variant="body2">{p.positionTitle}{p.departmentName ? `, ${p.departmentName}` : ''} at {p.centreName}</Typography>)}
        </Box>
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>Roles (what you may do)</Typography>
          {roles.length === 0 && <Typography variant="body2">No access yet. Ask your administrator.</Typography>}
          {roles.map((r, i) => <Typography key={i} variant="body2">{r.roleName} at {r.scopeName}{r.validTo ? ` until ${r.validTo}` : ''}</Typography>)}
        </Box>
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>Responsibilities (accountability, no access)</Typography>
          {responsibilities.length === 0 && <Typography variant="body2">None.</Typography>}
          <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
            {responsibilities.map((r) => <Chip key={r.id} size="small" label={`${r.name} · ${r.scopeName}`} />)}
          </Stack>
        </Box>
      </Box>
    </Paper>
  );
}

/** The live access guide: everyone's own access; administrators the whole model. */
function AccessGuideCard({ administrator }: { administrator: boolean }) {
  return (
    <Paper component={RouterLink} to="/help/access"
      sx={{ p: 2, mb: 3, display: 'block', textDecoration: 'none', color: 'inherit', borderLeft: 4, borderColor: 'primary.main', '&:hover': { bgcolor: 'action.hover' } }}>
      <Typography variant="h3">Access guide</Typography>
      <Typography variant="body2" color="text.secondary">
        {administrator
          ? 'The whole access model, live: licence modules, every permission, role, position and menu, who holds what, and the rules.'
          : 'What you can do, where, through which role, and which menus that opens. Built from your current roles.'}
      </Typography>
    </Paper>
  );
}

/** The knowledge centre: only the chapters for the signed-in person's work. */
export function HelpHome() {
  const [q, setQ] = useState('');
  const help = useHelp();
  const chapters = help.data?.chapters ?? [];
  const books = (Object.keys(BOOKS) as BookId[]).filter((b) => chaptersOf(chapters, b).length > 0);
  return (
    <>
      <PageHeader title="Help centre" subtitle="The chapters for your position and roles. Ask your administrator if you need help with something you do not see here." />
      <YourWork />
      <AccessGuideCard administrator={!!help.data?.administrator} />
      <SearchBox value={q} onChange={setQ} />
      <ProblemAlert error={help.error} />
      {q.trim().length > 1 && <Results chapters={chapters} query={q} />}
      {books.map((book) => (
        <Box key={book} sx={{ mb: 4 }}>
          <Typography variant="h2">{BOOKS[book].title}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{BOOKS[book].description}</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' }, gap: 1.5 }}>
            {chaptersOf(chapters, book).map((c) => (
              <Paper key={c.slug} component={RouterLink} to={`/help/${book}/${c.slug}`}
                sx={{ p: 2, textDecoration: 'none', color: 'inherit', '&:hover': { bgcolor: 'action.hover' } }}>
                <Typography variant="h3">{c.title}</Typography>
                <Typography variant="body2" color="text.secondary">{c.summary}</Typography>
              </Paper>
            ))}
          </Box>
        </Box>
      ))}
    </>
  );
}

export function HelpChapter() {
  const { book = '', slug = '' } = useParams();
  const { hash } = useLocation();
  const help = useHelp();
  const all = help.data?.chapters ?? [];
  const chapter = all.find((c) => c.book === book && c.slug === slug);
  const chapters = chaptersOf(all, (book as BookId) in BOOKS ? (book as BookId) : 'manual');
  const [q, setQ] = useState('');
  useEffect(() => {
    if (hash) {
      document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: 'start' });
    } else {
      window.scrollTo(0, 0);
    }
  }, [hash, slug, help.data]);
  if (help.isLoading) {
    return <Typography color="text.secondary">Loading…</Typography>;
  }
  if (!chapter) {
    return (
      <>
        <PageHeader title="This help page is not available to you" />
        <Alert severity="info" sx={{ mb: 2 }}>It may be about work outside your position and roles, or it no longer exists.</Alert>
        <Button component={RouterLink} to="/help">Back to your help centre</Button>
      </>
    );
  }
  const index = chapters.findIndex((c) => c.slug === chapter.slug);
  const prev = chapters[index - 1];
  const next = chapters[index + 1];
  const headings = chapter.sections.filter((s) => s.id);
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '240px 1fr', xl: '240px 1fr 220px' }, gap: 3, alignItems: 'start' }}>
      <Box component="nav" aria-label={`${BOOKS[chapter.book].title} chapters`} sx={{ position: { md: 'sticky' }, top: { md: 64 }, display: { xs: 'none', md: 'block' } }}>
        <List dense subheader={<ListSubheader disableSticky sx={{ px: 1 }}>{BOOKS[chapter.book].title}</ListSubheader>}>
          <ListItemButton component={RouterLink} to="/help" sx={{ borderRadius: 1 }}><ListItemText primary="← Help centre" /></ListItemButton>
          {chapters.map((c) => (
            <ListItemButton key={c.slug} component={RouterLink} to={`/help/${c.book}/${c.slug}`} selected={c.slug === chapter.slug} sx={{ borderRadius: 1 }}>
              <ListItemText primary={c.title} />
            </ListItemButton>
          ))}
        </List>
      </Box>
      <Box component="article" sx={{ minWidth: 0, maxWidth: 900 }}>
        <SearchBox value={q} onChange={setQ} />
        {q.trim().length > 1 && <Results chapters={all} query={q} />}
        <Typography variant="caption" color="text.secondary">
          <Link component={RouterLink} to="/help">Help centre</Link> › {BOOKS[chapter.book].title}
        </Typography>
        <Typography variant="h1" sx={{ mt: 0.5 }}>{chapter.title}</Typography>
        {chapter.summary && <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>{chapter.summary}</Typography>}
        <Paper sx={{ p: { xs: 2, md: 3 } }}>
          <Markdown source={chapter.body} />
        </Paper>
        <Stack direction="row" sx={{ justifyContent: 'space-between', mt: 2, mb: 4 }}>
          {prev ? <Button component={RouterLink} to={`/help/${prev.book}/${prev.slug}`}>← {prev.title}</Button> : <span />}
          {next ? <Button component={RouterLink} to={`/help/${next.book}/${next.slug}`}>{next.title} →</Button> : <span />}
        </Stack>
      </Box>
      {headings.length > 1 && (
        <Box component="nav" aria-label="On this page" sx={{ position: 'sticky', top: 64, display: { xs: 'none', xl: 'block' } }}>
          <Typography variant="caption" color="text.secondary">On this page</Typography>
          <Stack spacing={0.75} sx={{ mt: 1 }}>
            {headings.map((s) => <Link key={s.id} component={RouterLink} to={`#${s.id}`} variant="body2" underline="hover">{s.title}</Link>)}
          </Stack>
        </Box>
      )}
    </Box>
  );
}
