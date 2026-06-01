import tseslint from 'typescript-eslint';

const forbiddenAdapterImports = [
  '@nestjs/*',
  'hono',
  'bullmq',
  'ioredis',
  'class-validator',
  'class-transformer',
  'express',
  '**/internal/infrastructure/**',
  '@infrastructure/*',
  '../infrastructure/**',
  '../../infrastructure/**',
  '../../../infrastructure/**',
];

export default tseslint.config(
  ...tseslint.configs.recommended,
  {
    files: ['src/internal/domain/**/*.ts', 'src/internal/application/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: forbiddenAdapterImports.map((group) => ({
            group: [group],
            message: 'Clean Architecture boundary: domain/application must not import framework or infrastructure adapters.',
          })),
        },
      ],
    },
  },
);
