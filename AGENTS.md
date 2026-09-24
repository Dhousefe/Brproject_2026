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

## Baseline arquitetural e documentação

O mapa atual do sistema está em [`docs/architecture/system-map.md`](docs/architecture/system-map.md). Ele é a referência inicial para módulos, fluxo de execução, persistência e evolução para produção.

As seguintes regras valem para mudanças de arquitetura:

1. Antes de mover pacotes, renomear módulos ou separar serviços, atualizar o mapa de dependências e confirmar os pontos de entrada reais no código.
2. Não considerar um módulo como integrado apenas porque ele existe no Gradle ou está descrito no README. A integração precisa ser confirmada por imports, wiring de inicialização ou testes executáveis.
3. Alterações de persistência devem documentar o comportamento em caso de logout, shutdown normal, falha de processo e recuperação após crash.
4. O estado de runtime do jogador não deve ser versionado no Git. SQLite runtime, WAL/SHM, logs, caches, `hexid.txt` e arquivos gerados pertencem à máquina/ambiente e devem permanecer ignorados.
5. Documentação de produção deve separar claramente: estado atual local, decisão pretendida, riscos conhecidos e trabalho necessário para implementação.

### Constatações de persistência

- O GameServer em execução usa JDBC/Hikari diretamente, com SQLite em WAL; não há um segundo banco volátil responsável por fazer batch do estado dos jogadores.
- O autosave do jogador é agendado após 5 minutos e depois a cada 15 minutos em `GameClient`.
- Logout e desligamento normal chamam a rotina de armazenamento do jogador. Encerramento abrupto pode deixar no banco somente o último estado persistido.
- `modules/cluster-hpc` contém um protótipo independente de write-behind, mas o fluxo atual do GameServer não o utiliza. Qualquer documentação que o apresente como persistência ativa deve ser tratada como pendência de correção.
- O rollback observado de nível 80 para 79 é compatível, em primeiro lugar, com uma alteração de nível ainda não persistida antes do encerramento; a investigação deve adicionar telemetria de sucesso/falha do `store()` antes de alterar o modelo.
