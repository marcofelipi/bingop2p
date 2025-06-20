import org.jgroups.*;
import java.util.*;

public class Peer extends ReceiverAdapter {

    private JChannel canal;
    private String nome;
    private boolean isCoordenador = false;
    private Estados estado = Estados.INATIVO;
    private Set<Integer> bolasSorteadas = new HashSet<>();
    private Map<Address, Cartela> cartelas = new HashMap<>();
    private Cartela minhaCartela;
    private Address endereco;
    private Map<Address, String> votosAuditoria = new HashMap<>();
    private Address jogadorAuditado;

    public static void main(String[] args) throws Exception {
        new Peer().iniciar();
    }

    public void iniciar() throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite seu nome: ");
        nome = scanner.nextLine();

        canal = new JChannel();
        canal.setReceiver(this);
        canal.connect("BingoGrupo");

        endereco = canal.getAddress();
        
        //pega o primeiro peer a entrar e deixa ele como coordenador
        Address primeiro = canal.getView().getMembers().get(0);
        if (endereco.equals(primeiro)) {
            isCoordenador = true;
            System.out.println("Você é o COORDENADOR.");
        } else {
            System.out.println("Você é um JOGADOR.");
        }

        menu(scanner);
        canal.close();
    }

    private void menu(Scanner scanner) throws Exception {
        while (true) {
            if (estado == Estados.ENCERRADO) return;

            System.out.println("\nMenu " + (isCoordenador ? "Coordenador" : "Jogador"));
            if (isCoordenador) {
                System.out.println("1 - Abrir inscrições");
                System.out.println("2 - Iniciar jogo");
                System.out.println("3 - Sortear bola");
                System.out.println("4 - Encerrar jogo");
            } else {
                System.out.println("1 - Ver cartela");
                System.out.println("2 - Gritar BINGO!");
            }
            System.out.print("> ");
            String opcao = scanner.nextLine();

            if (isCoordenador) {
                switch (opcao) {
                    //abre inscricoes
                    case "1":
                        estado = Estados.INSCRICOES;
                        System.out.println("Inscrições abertas.");
                        break;
                    //distribui cartelas
                    case "2":
                        distribuirCartelas();
                        estado = Estados.EM_ANDAMENTO;
                        break;
                    //sorteia 1 bola
                    case "3":
                        sortearBola();
                        break;
                    case "4":
                    //encerra o jogo
                        canal.send(new Message(null, (Object) "FIM"));
                        estado = Estados.ENCERRADO;
                        break;
                }
            } else {
                // opcoes do jogador
                switch (opcao) {
                    //ver propria cartela
                    case "1":
                        System.out.println(minhaCartela != null ? minhaCartela : "Ainda sem cartela.");
                        break;
                        
                    //grita bingo
                    case "2":
                        if (minhaCartela != null && minhaCartela.venceu()) {
                            canal.send(new Message(null, (Object) ("BINGO:" + nome)));
                        } else {
                            System.out.println("Você ainda não venceu!");
                        }
                        break;
                }
            }
        }
    }

    private void distribuirCartelas() throws Exception {
        for (Address addr : canal.getView().getMembers()) {
            if (!addr.equals(endereco)) {
                Cartela c = Cartela.gerar();
                cartelas.put(addr, c);
                canal.send(new Message(addr, (Object) ("CARTELA:" + c.toString())));
            }
        }
        System.out.println("Cartelas distribuídas.");
    }

    private void sortearBola() throws Exception {
        if (estado != Estados.EM_ANDAMENTO) return;

        int bola;
        do {
            bola = new Random().nextInt(10) + 1;
        } while (bolasSorteadas.contains(bola));

        bolasSorteadas.add(bola);
        canal.send(new Message(null, (Object) ("BOLA:" + bola)));
        System.out.println("Bola sorteada: " + bola);
    }

    @Override
    public void receive(Message msg) {
        String m = msg.getObject().toString();

        try {
            if (m.startsWith("CARTELA:")) {
                minhaCartela = Cartela.fromString(m.replace("CARTELA:", "").trim());
                System.out.println("Recebeu cartela: " + minhaCartela);

            } else if (m.startsWith("BOLA:")) {
                int bola = Integer.parseInt(m.replace("BOLA:", "").trim());
                if (minhaCartela != null) minhaCartela.registrarBola(bola);
                bolasSorteadas.add(bola);
                System.out.println("Saiu bola: " + bola);

            } else if (m.startsWith("BINGO:")) {
                String nomeJogador = m.split(":")[1];
                Address enderecoJogador = msg.getSrc(); // <--- CAPTURE O ENDEREÇO AQUI

                System.out.println("⚠ Jogador " + nomeJogador + " (" + enderecoJogador + ") gritou BINGO!");

                if (isCoordenador) {
                    // Passe o endereço e o nome para a auditoria
                    iniciarAuditoria(enderecoJogador, nomeJogador);
                }

            } else if (m.startsWith("AUDITAR:")) {
                // Desmembra a mensagem usando a expressão regular para os separadores : e |
                String[] partes = m.split("[:|]");
                // partes[0] = "AUDITAR"
                // partes[1] = nome do jogador
                // partes[2] = string da cartela
                // partes[3] = string das bolas sorteadas

                String nomeAuditado = partes[1];
                Cartela cartelaAuditada = Cartela.fromString(partes[2]);

                // Pega o conjunto de bolas enviado pelo coordenador (fonte da verdade)
                Set<Integer> bolasOficiais = Cartela.fromString(partes[3]).getNumeros(); // Reutilizando o parser

                System.out.println("Convocado para auditar " + nomeAuditado + ". Cartela: " + cartelaAuditada + ". Bolas oficiais: " + bolasOficiais);

                // REGRA IMPORTANTE: O jogador que gritou bingo não vota.
                if (this.nome.equals(nomeAuditado)) {
                    System.out.println("Estou sendo auditado, não vou votar.");
                    return;
                }

                // Agora a validação usa o conjunto de bolas enviado pelo coordenador
                boolean valido = true;
                for (int numeroNaCartela : cartelaAuditada.getNumeros()) {
                    if (!bolasOficiais.contains(numeroNaCartela)) {
                        valido = false;
                        break;
                    }
                }

                if (valido) {
                    System.out.println("Meu voto: SIM para " + nomeAuditado);
                    canal.send(new Message(null, (Object) ("SIM:" + nomeAuditado)));
                } else {
                    System.out.println("Meu voto: NAO para " + nomeAuditado);
                    canal.send(new Message(null, (Object) ("NAO:" + nomeAuditado)));
                }

            } else if (m.startsWith("SIM:") || m.startsWith("NAO:")) {
                if (!isCoordenador || estado != Estados.AUDITORIA) return;
                Address de = msg.getSrc();
                votosAuditoria.put(de, m.startsWith("SIM") ? "SIM" : "NAO");

                int total = canal.getView().getMembers().size() - 1;
                if (votosAuditoria.size() >= total) {
                    long votosSim = votosAuditoria.values().stream().filter(v -> v.equals("SIM")).count();
                    if (votosSim > total / 2) {
                        System.out.println("BINGO confirmado!");
                        canal.send(new Message(null, (Object) "FIM"));
                        estado = Estados.ENCERRADO;
                    } else {
                        System.out.println("Jogador eliminado. Bingo inválido.");
                        cartelas.remove(jogadorAuditado);
                        estado = Estados.EM_ANDAMENTO;
                    }
                }

            } else if (m.equals("FIM")) {
                System.out.println("Jogo encerrado.");
                estado = Estados.ENCERRADO;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarAuditoria(Address enderecoJogador, String nomeJogador) throws Exception {
        estado = Estados.AUDITORIA;
        votosAuditoria.clear();
        jogadorAuditado = enderecoJogador;

        if (jogadorAuditado == null) {
            System.out.println("Jogador não encontrado para auditoria.");
            estado = Estados.EM_ANDAMENTO;
            return;
        }

        Cartela c = cartelas.get(jogadorAuditado);
        String bolasSorteadasStr = bolasSorteadas.toString();
        
        String msgAuditoria = "Auditar: "+nomeJogador+"|"+c.toString()+"|"+bolasSorteadasStr;
        canal.send(new Message(null, (Object) msgAuditoria));
        System.out.println("Auditoria iniciada para " + nomeJogador);
    }
}
