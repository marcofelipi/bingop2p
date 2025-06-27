import org.jgroups.*;
import java.util.*;

public class Peer extends ReceiverAdapter {

    private JChannel canal;
    private String meuNome;
    private boolean souCoordenador = false;
    private Estados estado = Estados.INATIVO;

    private Set<Integer> bolasJaSorteadas = new HashSet<>();
    private Map<Address, Cartela> cartelasJogadores = new HashMap<>();
    private Cartela minhaCartela;
    private Address meuEndereco;
    private View ultimaView;

    private Map<Address, String> votos = new HashMap<>();
    private Address jogadorQueGritouBingo;
    private Set<Address> eliminados = new HashSet<>();
    private boolean fuiEliminado = false;

    public static void main(String[] args) throws Exception {
        new Peer().iniciar();
    }

    public void iniciar() throws Exception {
        Scanner entrada = new Scanner(System.in);
        System.out.print("Digite seu nome: ");
        meuNome = entrada.nextLine();

        canal = new JChannel();
        canal.setReceiver(this);
        canal.connect("GrupoBingo");

        meuEndereco = canal.getAddress();

        menu(entrada);
        canal.close();
    }

    private void menu(Scanner entrada) throws Exception {
        while (true) {
            if (estado == Estados.ENCERRADO) return;
            if (estado == Estados.AGUARDANDO) {
                System.out.println("Esperando nova rodada...");
                continue;
            }

            System.out.println("\nMenu - " + (souCoordenador ? "Coordenador" : "Jogador"));

            if (souCoordenador) {
                System.out.println("1 - Abrir inscrições");
                System.out.println("2 - Começar jogo");
                System.out.println("3 - Sortear número");
                System.out.println("4 - Encerrar jogo");
            } else {
                System.out.println("1 - Ver minha cartela");
                System.out.println("2 - Gritar BINGO!");
            }

            System.out.print("> ");
            String opcao = entrada.nextLine();

            if (souCoordenador) {
                switch (opcao) {
                    case "1":
                        if(estado == Estados.EM_ANDAMENTO){
                            System.out.println("Termine o jogo em andamento!");
                            break;
                        }
                        estado = Estados.INSCRICOES;
                        System.out.println("Inscrições abertas!");
                        break;
                    case "2":
                        if(estado == Estados.EM_ANDAMENTO){
                            System.out.println("Jogo já está em andamento.");
                            break;
                        }
                        enviarCartelas();
                        estado = Estados.EM_ANDAMENTO;
                        break;
                    case "3":
                        sortearNumero();
                        break;
                    case "4":
                        canal.send(new Message(null, "FIM"));
                        estado = Estados.ENCERRADO;
                        break;
                }
            } else {
                switch (opcao) {
                    case "1":
                        System.out.println(minhaCartela != null ? minhaCartela : "Sem cartela ainda.");
                        break;
                    case "2":
                        if (fuiEliminado) {
                            System.out.println("Você foi eliminado nesta rodada.");
                        } else if (minhaCartela != null) {
                            String mensagem = "BINGO:" + meuNome + "|" + minhaCartela;
                            canal.send(new Message(null, mensagem));
                        } else {
                            System.out.println("Você ainda não tem cartela.");
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void viewAccepted(View novaView) {
        if (meuEndereco == null) meuEndereco = canal.getAddress();

        if (ultimaView == null) {
            System.out.println("Entrou no grupo: " + novaView);
            if (meuEndereco.equals(novaView.getMembers().get(0))) {
                System.out.println("Você é o COORDENADOR.");
                souCoordenador = true;
            } else {
                System.out.println("Você é um JOGADOR.");
            }
            ultimaView = novaView;
            return;
        }

        Address coordAntigo = ultimaView.getMembers().get(0);
        if (!novaView.getMembers().contains(coordAntigo)) {
            Address novoCoord = novaView.getMembers().get(0);
            if (meuEndereco.equals(novoCoord)) {
                System.out.println("Assumindo como novo coordenador. Solicitando estado...");
                try {
                    canal.send(new Message(null, "ENVIAR_ESTADO"));
                } catch (Exception ex) {
                    System.getLogger(Peer.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
                souCoordenador = true;
                minhaCartela = null;
                fuiEliminado = false;
                estado = Estados.EM_ANDAMENTO;
            }
        }
        ultimaView = novaView;
    }
    
    private void enviarCartelas() throws Exception {
        if (estado != Estados.INSCRICOES) {
            System.out.println("Inscrições não estão abertas!");
            return;
        }

        // Fecha as inscrições
        estado = Estados.EM_ANDAMENTO;
        System.out.println("Inscrições fechadas. Gerando cartelas...");

        // Limpa cartelas anteriores
        cartelasJogadores.clear();
        bolasJaSorteadas.clear();
        eliminados.clear();
        fuiEliminado = false;

        // Gera e envia cartelas para todos (incluindo coordenador)
        for (Address addr : canal.getView().getMembers()) {
            Cartela nova = Cartela.gerar();
            cartelasJogadores.put(addr, nova);

            // Envia a cartela identificando o dono pelo Address
            String mensagem = "CARTELA_PARA:" + nova;
            canal.send(new Message(addr, mensagem));

            System.out.println("Cartela enviada para " + addr);
        }

        minhaCartela = cartelasJogadores.get(meuEndereco);
        System.out.println("Cartelas distribuídas! Jogo iniciado.");
    }

    private void sortearNumero() throws Exception {
        if (estado != Estados.EM_ANDAMENTO) return;

        int numero;
        do {
            numero = new Random().nextInt(10) + 1;
        } while (bolasJaSorteadas.contains(numero));

        bolasJaSorteadas.add(numero);
        canal.send(new Message(null, "NUMERO:" + numero));
        System.out.println("Número sorteado: " + numero);
    }

    @Override
    public void receive(Message msg) {
        String conteudo = msg.getObject().toString();

        try {
            if (conteudo.startsWith("CARTELA:")) {
                Cartela cartela = Cartela.deTexto(conteudo.replace("CARTELA:", ""));
                minhaCartela = cartela;
                System.out.println("\n--- SUA CARTELA ---");
                System.out.println("Cartela: " + minhaCartela);
                System.out.println("------------------");
            
            } else if (conteudo.startsWith("CARTELA_RECUPERADA:")) {
                if (souCoordenador) {
                    Address jogador = msg.getSrc();
                    Cartela cartela = Cartela.deTexto(conteudo.replace("CARTELA_RECUPERADA:", ""));
                    cartelasJogadores.put(jogador, cartela);
                }

            } else if (conteudo.startsWith("BOLAS_RECUPERADAS:")) {
                if (souCoordenador) {
                    String lista = conteudo.replace("BOLAS_RECUPERADAS:", "").replaceAll("[\\[\\] ]", "");
                    String[] partes = lista.split(",");
                    for (String num : partes) {
                        if (!num.isEmpty()){
                            bolasJaSorteadas.add(Integer.valueOf(num));
                        }
                    }
                }
            } else if (conteudo.startsWith("NUMERO:")) {
                int num = Integer.parseInt(conteudo.replace("NUMERO:", ""));
                if (minhaCartela != null) minhaCartela.marcar(num);
                bolasJaSorteadas.add(num);
                System.out.println("Saiu o número: " + num);

            } else if (conteudo.startsWith("BINGO:")) {
                String[] partes = conteudo.split("[:|]");
                String nome = partes[1];
                Cartela cartela = Cartela.deTexto(partes[2]);
                Address jogador = msg.getSrc();

                System.out.println(nome + " gritou BINGO! Cartela: " + cartela);

                if (souCoordenador && !eliminados.contains(jogador)) {
                    comecarAuditoria(jogador, nome, cartela);
                }

            } else if (conteudo.startsWith("AUDITAR:")) {
                String[] partes = conteudo.split("[:|]");
                String nome = partes[1];
                Cartela cartela = Cartela.deTexto(partes[2]);
                Set<Integer> bolas = Cartela.deTexto(partes[3]).getNumeros();

                if (meuNome.equals(nome)) {
                    System.out.println("Aguardando votação dos outros jogadores...");
                    return; // não voto em mim mesmo
                }

                // Mostra informações para votação
                System.out.println("\n--- AUDITORIA ---");
                System.out.println("Jogador " + nome + " gritou BINGO!");
                System.out.println("Cartela do jogador: " + cartela);
                System.out.println("Números sorteados: " + bolas);

                // Solicita voto
                System.out.println("Você acredita que o BINGO é válido? (S/N)");
                System.out.print("> ");

                // Lê a resposta do terminal
                Scanner scanner = new Scanner(System.in);
                String resposta = scanner.nextLine().toUpperCase();

                // Valida a entrada
                while (!resposta.equals("S") && !resposta.equals("N")) {
                    System.out.println("Por favor, digite S (Sim) ou N (Não):");
                    System.out.print("> ");
                    resposta = scanner.nextLine().toUpperCase();
                }

                // Envia o voto
                String voto = resposta.equals("S") ? "SIM:" : "NAO:";
                canal.send(new Message(null, voto + nome));

            } else if (conteudo.startsWith("SIM:") || conteudo.startsWith("NAO:")) {
                if (!souCoordenador || estado != Estados.AUDITORIA) return;

                String nome = conteudo.substring(4);
                votos.put(msg.getSrc(), conteudo.startsWith("SIM") ? "SIM" : "NAO");

                System.out.println("Recebido voto " + conteudo.substring(0, 3) + " de " + nome);

                int totalJogadores = ultimaView.getMembers().size() - 2; // -1 para o coordenador, -1 para o jogador que gritou bingo
                if (totalJogadores <= 0) totalJogadores = 1;

                if (votos.size() >= totalJogadores) {
                    long sim = votos.values().stream().filter(v -> v.equals("SIM")).count();
                    if (sim > totalJogadores / 2.0) {
                        System.out.println("BINGO confirmado! Jogo encerrado.");
                        canal.send(new Message(null, "FIM"));
                        estado = Estados.ENCERRADO;
                    } else {
                        System.out.println("Bingo falso! Jogador eliminado.");
                        eliminados.add(jogadorQueGritouBingo);
                        cartelasJogadores.remove(jogadorQueGritouBingo);
                        canal.send(new Message(jogadorQueGritouBingo, "ELIMINADO"));
                        estado = Estados.EM_ANDAMENTO;
                    }
                    votos.clear();
                }

            } else if (conteudo.equals("FIM")) {
                System.out.println("O jogo acabou.");
                estado = Estados.INATIVO;

            } else if (conteudo.equals("ELIMINADO")) {
                System.out.println("Você foi eliminado dessa rodada!");
                fuiEliminado = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void comecarAuditoria(Address jogador, String nome, Cartela cartela) throws Exception {
        estado = Estados.AUDITORIA;
        votos.clear();
        jogadorQueGritouBingo = jogador;

        String msg = "AUDITAR:" + nome + "|" + cartela + "|" + bolasJaSorteadas;
        for (Address addr : canal.getView().getMembers()) {
            if (!addr.equals(meuEndereco) && !addr.equals(jogador)) {
                canal.send(new Message(addr, msg));
            }
        }
    }

}