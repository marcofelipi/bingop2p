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
    private Address meuEndereco;
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

        meuEndereco = canal.getAddress();
        Address primeiro = canal.getView().getMembers().get(0);
        if (meuEndereco.equals(primeiro)) {
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
                    case "1":
                        estado = Estados.INSCRICOES;
                        System.out.println("Inscrições abertas.");
                        break;
                    case "2":
                        distribuirCartelas();
                        estado = Estados.EM_ANDAMENTO;
                        break;
                    case "3":
                        sortearBola();
                        break;
                    case "4":
                        canal.send(new Message(null, (Object) "FIM"));
                        estado = Estados.ENCERRADO;
                        break;
                }
            } else {
                switch (opcao) {
                    case "1":
                        System.out.println(minhaCartela != null ? minhaCartela : "Ainda sem cartela.");
                        break;
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
            if (!addr.equals(meuEndereco)) {
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
                String[] partes = m.split(":", 3);
                String nomeJogador = partes[1];
                Cartela c = Cartela.fromString(partes[2]);

                boolean valido = true;
                for (int n : c.getNumeros()) {
                    if (!bolasSorteadas.contains(n)) {
                        valido = false;
                        break;
                    }
                }

                canal.send(new Message(null, (Object) (valido ? "SIM:" : "NAO:") + nomeJogador));

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
        canal.send(new Message(null, (Object) "AUDITAR:" + nomeJogador + ":" + c.toString()));
        System.out.println("Auditoria iniciada para " + nomeJogador);
    }
}
