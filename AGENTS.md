# Padrão de desenvolvimento L2 NewEra

## Fluxo obrigatório para mudanças

Toda feature nova deve ser desenvolvida em uma branch criada a partir de `main`, seguindo o padrão:

```text
feature/<numero>-<nome-da-feature>
```

Todo fix/hotfix deve seguir:

```text
fix/<numero>-<nome-do-fix>
```

O número deve ser sequencial e o nome deve ser curto, descritivo e em kebab-case.

Para cada solicitação de feature, fix ou hotfix:

1. Atualizar `main` com `git pull --ff-only` e criar a branch correspondente a partir dela; nunca desenvolver diretamente em `main`.
2. Implementar a mudança na branch de trabalho.
3. Compilar e validar o projeto.
4. Fazer commit com uma mensagem seguindo Conventional Commits.
5. Publicar a branch no remoto.
6. Criar o Pull Request da branch de trabalho para `main`; não fazer merge automático localmente.
7. Aguardar o merge do Pull Request antes de iniciar a próxima feature/fix.
8. Após o merge aprovado, atualizar `main` localmente e iniciar LoginServer, GameServer e Proxy a partir de `main` para validação.
9. Informar o commit, a branch e o resultado da compilação/inicialização.

Alterações de runtime, banco local, caches, certificados e arquivos gerados não devem ser commitadas. A branch `main` recebe mudanças somente por Pull Request. A branch `dev` fica fora do fluxo padrão enquanto o projeto tiver um único desenvolvedor; se for retomada no futuro, deverá ser explicitamente solicitada.

## Convenções de commits

Usar, conforme o caso: `feat:`, `fix:`, `hotfix:`, `refactor:`, `docs:`, `test:` ou `chore:`.

## Ambiente local

O ambiente local usa SQLite e localhost. Para executar os módulos com segurança, usar `--no-daemon --no-parallel` e um `GRADLE_USER_HOME` dentro do projeto.
