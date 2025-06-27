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

- Método `receive`
  - Responsável por lidar com todas as mensagens recebidas no canal JGroups.
  - Usa condicionais para tratar diferentes tipos de mensagens:
    - `ANUNCIO_NOME:<nome>`: um peer avisa seu nome ao entrar no grupo.
    - `SOLICITAR_ESTADO`: novo coordenador solicita o estado atual do jogo.
    - `NUMERO:<n>`: envia o número sorteado para todos os peers.
    - `CARTELA_DONO:<endereco>:<cartela>`: o coordenador distribui as cartelas geradas.
    - `BINGO:<nome>|<cartela>`: um jogador grita bingo, inicia-se a auditoria.
    - `AUDITAR:<nome>|<cartela>|<numeros>`: os peers conferem se o bingo é válido.
    - `SIM:<nome>` / `NAO:<nome>`: votos dos peers sobre a validade do bingo.
    - `VOCE_GANHOU`: mensagem de vitória enviada ao jogador que teve o bingo validado.
    - `ELIMINADO`: jogador é eliminado após bingo falso.
    - `RESET`: reinicia o estado interno do jogo para nova rodada.
    - `CONTINUAR_JOGO`: bingo falso confirmado, jogo continua.
    - `JOGO_INICIADO`: sinal de que a rodada começou.
    - `FIM_GERAL`: jogo encerrado pelo coordenador.

- A comunicação é assíncrona:
  - Cada peer reage às mensagens conforme seu papel no jogo.
  - O coordenador toma decisões com base nos votos e no estado atual do jogo.

- A auditoria é feita de forma automática, cada peer compara a cartela do jogador que gritou Bingo com todos os números sorteados do coordenador.
- Os peers trocam as mensagens pelo método receive. Cada mensagem tem um tipo de tratamento.
- Se o bingo for falso, o jogador é eliminado e o jogo continua normalmente.
