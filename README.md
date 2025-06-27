# BINGO NA ARQUITETURA PEER-TO-PEER

## Estrutura P2P

- Sem servidor central
- Todos os peers são iguais

## Gerenciamento do grupo

- O JGroups encontra os peers na mesma rede conectados ao Bingo
- O primeiro peer a entrar será o coordenador
- O grupo é gerenciado pelo método VIEWACCEPTED
    - O JGroups chama o VIEWACCEPTED toda vez que alguém entra ou sai do grupo
    - Quando um novo peer entra, todos os outros antigos veem ele e ele vê os antigos
    - Se o coordenador sair, o VIEWACCEPTED detecta e transforma o segundo peer a entrar em coordenador.

## Comunicação

- Método RECEIVE
    - 
