import { createTheme } from '@mui/material/styles';

import { tokens } from './tokens';

function palette(mode: 'light' | 'dark') {
  const c = tokens[mode];
  return {
    mode,
    primary: { main: c.primary, contrastText: c.primaryContrast },
    success: { main: c.success },
    warning: { main: c.warning },
    error: { main: c.danger },
    info: { main: c.info },
    secondary: { main: c.demo },
    background: { default: c.background, paper: c.surface },
    text: { primary: c.text, secondary: c.textMuted },
    divider: c.border,
  };
}

/** Compact, readable institutional theme: 14 px base, dense controls, strong contrast. */
export const theme = createTheme({
  cssVariables: { colorSchemeSelector: 'class' },
  colorSchemes: { light: { palette: palette('light') }, dark: { palette: palette('dark') } },
  shape: { borderRadius: tokens.radius },
  spacing: tokens.space,
  typography: {
    fontFamily: tokens.font.family,
    fontSize: tokens.font.size.md,
    h1: { fontSize: tokens.font.size.xxl, fontWeight: 600 },
    h2: { fontSize: tokens.font.size.xl, fontWeight: 600 },
    h3: { fontSize: tokens.font.size.lg, fontWeight: 600 },
    body1: { fontSize: tokens.font.size.md },
    body2: { fontSize: tokens.font.size.sm },
    caption: { fontSize: tokens.font.size.xs },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { size: 'small', disableElevation: true } },
    MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
    MuiSelect: { defaultProps: { size: 'small' } },
    MuiAutocomplete: { defaultProps: { size: 'small' } },
    MuiTable: { defaultProps: { size: 'small' } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 600, whiteSpace: 'nowrap' } } },
    MuiChip: { defaultProps: { size: 'small' } },
    MuiPaper: { defaultProps: { variant: 'outlined' } },
    MuiTooltip: { defaultProps: { arrow: true } },
    MuiDialog: { defaultProps: { fullWidth: true, maxWidth: 'sm' } },
  },
});
