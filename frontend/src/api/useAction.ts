import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useNotify } from '../components/Notifier';

/** A business action: runs, refreshes the named queries, confirms in plain words. */
export function useAction<TArgs, TResult>(
  run: (args: TArgs) => Promise<TResult>,
  options: { invalidate?: unknown[][]; success?: string | ((r: TResult) => string); onDone?: (r: TResult) => void } = {},
) {
  const client = useQueryClient();
  const notify = useNotify();
  return useMutation<TResult, unknown, TArgs>({
    mutationFn: run,
    onSuccess: async (result) => {
      await Promise.all((options.invalidate ?? []).map((key) => client.invalidateQueries({ queryKey: key })));
      await client.invalidateQueries({ queryKey: ['me'] });
      if (options.success) {
        notify(typeof options.success === 'function' ? options.success(result) : options.success);
      }
      options.onDone?.(result);
    },
  });
}
