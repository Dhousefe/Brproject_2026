# Padrão de desenvolvimento L2 NewEra

## Fluxo obrigatório para mudanças

Toda feature nova deve ser desenvolvida em uma branch criada a partir de `dev`, seguindo o padrão:

```text
feature/<numero>-<nome-da-feature>
```

Todo fix/hotfix deve seguir:

```text
fix/<numero>-<nome-do-fix>
```

O número deve ser sequencial e o nome deve ser curto, descritivo e em kebab-case.

Para cada solicitação de feature, fix ou hotfix:

1. Criar ou usar a branch correspondente; nunca desenvolver diretamente em `main` ou `dev`.
2. Implementar a mudança na branch de trabalho.
3. Compilar e validar o projeto.
4. Fazer commit com uma mensagem seguindo Conventional Commits.
5. Publicar a branch no remoto.
6. Mudar para `dev` e fazer merge da branch de trabalho em `dev`.
7. Publicar `dev` no remoto.
8. Iniciar LoginServer, GameServer e Proxy a partir de `dev` para homologação local.
9. Informar o commit, a branch e o resultado da compilação/inicialização.

Alterações de runtime, banco local, caches, certificados e arquivos gerados não devem ser commitadas. A branch `main` recebe mudanças somente por Pull Request; `dev` é o ambiente de desenvolvimento/homologação.

## Convenções de commits

Usar, conforme o caso: `feat:`, `fix:`, `hotfix:`, `refactor:`, `docs:`, `test:` ou `chore:`.

## Ambiente local

O ambiente local usa SQLite e localhost. Para executar os módulos com segurança, usar `--no-daemon --no-parallel` e um `GRADLE_USER_HOME` dentro do projeto.
