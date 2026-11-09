import {
  AppBar,
  Badge,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  ListSubheader,
  Menu,
  MenuItem,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import AccountCircle from '@mui/icons-material/AccountCircle';
import HelpIcon from '@mui/icons-material/HelpOutlined';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';

import { useSession } from '../auth/session';
import { chapterForPath } from '../help/content';
import { signOut } from '../auth/signin';
import { visibleItems, type NavItem } from './navigation';

const DRAWER_WIDTH = 248;

export function AppShell() {
  const session = useSession();
  const { me } = session;
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const location = useLocation();
  const items = visibleItems(session.hasAnywhere);
  const groups = ['Workspace', 'Clinic', 'Pharmacy and optical', 'Organisation', 'People', 'Access', 'Help'] as const;
  const centres = me.centres ?? [];

  const centreSelect = centres.length > 0 && (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Typography variant="body2" color="text.secondary" component="label" htmlFor="working-centre" sx={{ whiteSpace: 'nowrap' }}>
        Working centre
      </Typography>
      <TextField
        select
        id="working-centre"
        hiddenLabel
        value={session.centreId ?? ''}
        onChange={(e) => session.setCentreId(e.target.value)}
        sx={{ width: { xs: '100%', md: 280 } }}
        slotProps={{ select: { inputProps: { 'aria-label': 'Working centre' } } }}
      >
        {centres.map((c) => (
          <MenuItem key={c.centreId} value={c.centreId}>
            {c.name}
          </MenuItem>
        ))}
      </TextField>
    </Box>
  );

  const nav = (
    <Box component="nav" aria-label="Main navigation" sx={{ py: 1 }}>
      {!desktop && centreSelect && <Box sx={{ px: 2, pb: 1 }}>{centreSelect}</Box>}
      {groups.map((g) => {
        const inGroup = items.filter((i: NavItem) => i.group === g);
        if (inGroup.length === 0) {
          return null;
        }
        return (
          <List key={g} dense subheader={<ListSubheader disableSticky>{g}</ListSubheader>}>
            {inGroup.map((i) => (
              <ListItemButton
                key={i.to}
                component={NavLink}
                to={i.to}
                selected={i.to === '/' || i.to === '/help' ? location.pathname === i.to : location.pathname.startsWith(i.to)}
                onClick={() => setMobileOpen(false)}
              >
                <ListItemText primary={i.label} />
                {i.to === '/approvals' && (me.pendingApprovals ?? 0) > 0 && (
                  <Badge color="warning" badgeContent={me.pendingApprovals} sx={{ mr: 1 }} />
                )}
              </ListItemButton>
            ))}
          </List>
        );
      })}
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" color="default" elevation={0} sx={{ zIndex: (t) => t.zIndex.drawer + 1, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Toolbar variant="dense" sx={{ gap: 1.5 }}>
          {!desktop && (
            <IconButton edge="start" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h3" component="div" sx={{ whiteSpace: 'nowrap' }}>
            core-neha
          </Typography>
          {me.demoTenant && (
            <Tooltip title="Synthetic demonstration data only. Do not enter real patient or staff information.">
              <Chip label={desktop ? 'DEMONSTRATION' : 'DEMO'} color="secondary" variant="outlined" />
            </Tooltip>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ display: { xs: 'none', md: 'block' } }}>
            {me.tenantName}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          {desktop && centreSelect}
          <Tooltip title="Help for this screen">
            <IconButton aria-label="Help for this screen" component={NavLink} to={chapterForPath(location.pathname)}>
              <HelpIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title={me.displayName ?? me.username ?? ''}>
            <IconButton aria-label="Account menu" onClick={(e) => setMenuAnchor(e.currentTarget)}>
              <AccountCircle />
            </IconButton>
          </Tooltip>
          <Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={() => setMenuAnchor(null)}>
            <MenuItem disabled>
              <ListItemText primary={me.displayName} secondary={me.username} />
            </MenuItem>
            <Divider />
            <MenuItem onClick={signOut}>Sign out</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Drawer
        variant={desktop ? 'permanent' : 'temporary'}
        open={desktop || mobileOpen}
        onClose={() => setMobileOpen(false)}
        sx={{ width: DRAWER_WIDTH, flexShrink: 0, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
      >
        <Toolbar variant="dense" />
        {nav}
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3 } }}>
        <Toolbar variant="dense" />
        <Outlet />
      </Box>
    </Box>
  );
}
