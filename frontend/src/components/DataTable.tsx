import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState, type ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  /** Value used for sorting and the quick filter. */
  value?: (row: T) => string | number | undefined;
  width?: number | string;
  align?: 'left' | 'right' | 'center';
}

interface Props<T> {
  rows: T[] | undefined;
  columns: Column<T>[];
  rowKey: (row: T) => string;
  loading?: boolean;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
  filterPlaceholder?: string;
  dense?: boolean;
}

/** Compact sortable table with a quick filter. Keyboard: rows with an action are focusable and open with Enter. */
export function DataTable<T>({ rows, columns, rowKey, loading, empty, onRowClick, filterPlaceholder, dense = true }: Props<T>) {
  const [filter, setFilter] = useState('');
  const [sort, setSort] = useState<{ key: string; dir: 'asc' | 'desc' } | undefined>();

  const visible = useMemo(() => {
    let list = rows ?? [];
    const f = filter.trim().toLowerCase();
    if (f) {
      list = list.filter((r) =>
        columns.some((c) => String(c.value ? (c.value(r) ?? '') : '').toLowerCase().includes(f)),
      );
    }
    if (sort) {
      const col = columns.find((c) => c.key === sort.key);
      if (col?.value) {
        const get = col.value;
        list = [...list].sort((a, b) => {
          const av = get(a) ?? '';
          const bv = get(b) ?? '';
          const cmp = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv));
          return sort.dir === 'asc' ? cmp : -cmp;
        });
      }
    }
    return list;
  }, [rows, filter, sort, columns]);

  return (
    <Paper>
      {filterPlaceholder && (
        <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            placeholder={filterPlaceholder}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            slotProps={{ htmlInput: { 'aria-label': filterPlaceholder } }}
            sx={{ maxWidth: 360 }}
          />
        </Box>
      )}
      <TableContainer>
        <Table size={dense ? 'small' : 'medium'} stickyHeader>
          <TableHead>
            <TableRow>
              {columns.map((c) => (
                <TableCell key={c.key} align={c.align} sx={{ width: c.width }}>
                  {c.value ? (
                    <TableSortLabel
                      active={sort?.key === c.key}
                      direction={sort?.key === c.key ? sort.dir : 'asc'}
                      onClick={() =>
                        setSort((s) => (s?.key === c.key ? { key: c.key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key: c.key, dir: 'asc' }))
                      }
                    >
                      {c.header}
                    </TableSortLabel>
                  ) : (
                    c.header
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && (
              <TableRow>
                <TableCell colSpan={columns.length}>
                  <Typography variant="body2" color="text.secondary">Loading…</Typography>
                </TableCell>
              </TableRow>
            )}
            {!loading && visible.length === 0 && (
              <TableRow>
                <TableCell colSpan={columns.length}>
                  <Typography variant="body2" color="text.secondary">{empty ?? 'Nothing to show.'}</Typography>
                </TableCell>
              </TableRow>
            )}
            {visible.map((r) => (
              <TableRow
                key={rowKey(r)}
                hover={!!onRowClick}
                tabIndex={onRowClick ? 0 : undefined}
                onClick={onRowClick ? () => onRowClick(r) : undefined}
                onKeyDown={onRowClick ? (e) => e.key === 'Enter' && onRowClick(r) : undefined}
                sx={onRowClick ? { cursor: 'pointer' } : undefined}
              >
                {columns.map((c) => (
                  <TableCell key={c.key} align={c.align}>
                    {c.render(r)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
