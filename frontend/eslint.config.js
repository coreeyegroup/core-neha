import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'src/api/generated', 'node_modules', 'playwright-report', 'test-results'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-restricted-syntax': [
        'error',
        { selector: "Literal[value=/^#[0-9a-fA-F]{3,8}$/]", message: 'Use design tokens (src/theme) instead of raw colours.' },
      ],
    },
  },
  {
    files: ['src/theme/**'],
    rules: { 'no-restricted-syntax': 'off' },
  },
);
