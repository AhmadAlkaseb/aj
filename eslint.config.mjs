import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    files: ['frontend/src/main/resources/public/*.js'],
    ...js.configs.recommended,
    languageOptions: { globals: globals.browser },
    rules: {
      ...js.configs.recommended.rules,
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', caughtErrors: 'none' }],
      'no-eval': 'error',
      'no-implied-eval': 'error',
      eqeqeq: ['error', 'smart'],
    },
  },
  {
    files: ['tools/contracts/*.cjs'],
    ...js.configs.recommended,
    languageOptions: { sourceType: 'commonjs', globals: globals.node },
  },
];
