import org.jgroups.*;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class Peer extends ReceiverAdapter {

    private JChannel canal;
    private String meuNome;
    private boolean souCoordenador = false;
    private Estados estado = Estados.INATIVO;

    private Set<Integer> bolasJaSorteadas = new HashSet<>();
    private Map<String, Cartela> cartelasJogadores = new HashMap<>();
    private Cartela minhaCartela;
    private Address meuEndereco;
    private View ultimaView;

    private Map<Address, String> votos = new HashMap<>();
    private Address jogadorQueGritouBingo;
    private Set<String> eliminados = new HashSet<>();
    private boolean fuiEliminado = false;
    
    private int respostasDeEstadoRecebidas = 0;
    private final Map<Address, String> mapaDeNomes = new HashMap<>();

    private String getNome(Address addr) {
        if (addr == null) return "N/A";
        return mapaDeNomes.getOrDefault(addr, addr.toString().split("-")[0]);
    }

    public static void main(String[] args) throws Exception {
        new Peer().iniciar();
    }

    public void iniciar() throws Exception {
        Scanner entrada = new Scanner(System.in);
        System.out.print("Digite seu nome: ");
        meuNome = entrada.nextLine();

        System.setProperty("java.net.preferIPv4Stack", "true");
        canal = new JChannel("udp.xml");
        canal.setReceiver(this);
        canal.connect("BingoDaComunidade");

        System.out.println("\nBem-vindo ao Bingo da Comunidade, " + meuNome + "!");
        menu(entrada);
        canal.close();
    }

    private void menu(Scanner entrada) throws Exception {
        while (true) {
            if (estado == Estados.ENCERRADO) return;

            if (estado == Estados.AUDITORIA) {
                System.out.println("Auditoria em andamento... aguardando resultado do coordenador.");
                Thread.sleep(3000);
                continue;
            }
            if (estado == Estados.AGUARDANDO) {
                System.out.println("Recuperando estado do jogo apos falha do coordenador...");
                Thread.sleep(3000);
                continue;
            }
            if (!souCoordenador && estado == Estados.INATIVO) {
                System.out.println("Aguardando o Coordenador iniciar uma nova rodada...");
                Thread.sleep(5000);
                continue;
            }
            
            String role = souCoordenador ? "Coordenador" : "Jogador";
            System.out.println("\n--- MENU (" + role + ") | Estado: " + estado + " ---");

            if (souCoordenador) {
                System.out.println("1 - Iniciar nova rodada");
                System.out.println("2 - Sortear numero");
                System.out.println("3 - Encerrar Jogo (Sair)");
            } else {
                System.out.println("1 - Ver minha cartela");
                System.out.println("2 - Gritar BINGO!");
                System.out.println("3 - Sair");
            }

            System.out.print("[" + meuNome + "]> ");
            String opcao = entrada.nextLine();

            if (souCoordenador) {
                switch (opcao) {
                    case "1": iniciarNovaRodada(); break;
                    case "2": sortearNumero(); break;
                    case "3": canal.send(new Message(null, "FIM_GERAL")); return;
                }
            } else {
                switch (opcao) {
                    case "1": System.out.println(minhaCartela != null ? "Sua cartela: " + minhaCartela.getNumeros() : "Sem cartela ainda."); break;
                    case "2": gritarBingo(); break;
                    case "3": return;
                }
            }
        }
    }

    private void iniciarNovaRodada() throws Exception {
        if (estado == Estados.EM_ANDAMENTO) { System.out.println("Um jogo ja esta em andamento."); return; }
        canal.send(new Message(null, "RESET"));
        System.out.println("\n================ INICIANDO NOVA RODADA ================");
        System.out.println("Coordenador esta gerando e distribuindo as cartelas...");

        for (Address addr : ultimaView.getMembers()) {
            if (!addr.equals(meuEndereco)) {
                Cartela nova = Cartela.gerar();
                cartelasJogadores.put(addr.toString(), nova);
                String mensagem = "CARTELA_DONO:" + addr.toString() + ":" + nova.toString();
                canal.send(new Message(null, mensagem));
            }
        }
        minhaCartela = null;
        canal.send(new Message(null, "JOGO_INICIADO"));
    }
    
    private void gritarBingo() throws Exception {
        if (fuiEliminado) { System.out.println("Voce foi eliminado."); } 
        else if (estado == Estados.EM_ANDAMENTO && minhaCartela != null) {
            String mensagem = "BINGO:" + meuNome + "|" + minhaCartela;
            canal.send(new Message(null, mensagem));
            System.out.println("Voce gritou BINGO! Aguardando auditoria...");
        } else { System.out.println("Nao e possivel gritar BINGO agora."); }
    }
    
    private void sortearNumero() throws Exception {
        if (estado != Estados.EM_ANDAMENTO) { System.out.println("O jogo precisa estar em andamento para sortear."); return; }
        int numero;
        do { numero = new Random().nextInt(10) + 1; } while (bolasJaSorteadas.contains(numero));
        bolasJaSorteadas.add(numero);
        
        System.out.println("\n--- Coordenador sorteou um novo numero! ---");
        System.out.println("--> Numero Sorteado: " + numero);
        System.out.println("Historico de Sorteios: " + bolasJaSorteadas);
        System.out.println("-------------------------------------------");

        canal.send(new Message(null, "NUMERO:" + numero));
    }
    
    private void resetarParaNovaRodada() {
        System.out.println("\n================ PREPARANDO PARA NOVA RODADA ================");
        estado = Estados.INATIVO;
        bolasJaSorteadas.clear();
        cartelasJogadores.clear();
        minhaCartela = null;
        votos.clear();
        jogadorQueGritouBingo = null;
        fuiEliminado = eliminados.contains(meuEndereco.toString());
        respostasDeEstadoRecebidas = 0;
    }

    @Override
    public void viewAccepted(View novaView) {
        System.out.println("\n================ ATUALIZACAO DO GRUPO (VIEW) ================");
        
        if (meuEndereco == null) {
            meuEndereco = canal.getAddress();
            mapaDeNomes.put(meuEndereco, meuNome);
        }

        try {
            canal.send(new Message(null, "ANUNCIO_NOME:" + meuNome));
        } catch (Exception e) { e.printStackTrace(); }

        boolean souNovoCoordenador = false;
        if (ultimaView != null) {
            Address coordAntigo = ultimaView.getMembers().get(0);
            if (!novaView.getMembers().contains(coordAntigo)) {
                System.out.println("FALHA DETECTADA: O coordenador anterior ("+ getNome(coordAntigo) +") saiu!");
                if (meuEndereco.equals(novaView.getMembers().get(0))) {
                    souNovoCoordenador = true;
                }
            }
        } else {
            if (meuEndereco.equals(novaView.getMembers().get(0))) {
                System.out.println("Voce e o primeiro membro, voce e o COORDENADOR.");
                souCoordenador = true;
            } else {
                System.out.println("Voce entrou no grupo como JOGADOR.");
            }
        }
        
        if (souNovoCoordenador) {
            System.out.println("ASSUMINDO O CONTROLE: Fui promovido a novo COORDENADOR!");
            souCoordenador = true;
            minhaCartela = null;
            fuiEliminado = false;
            
            if (estado == Estados.EM_ANDAMENTO || estado == Estados.AUDITORIA) {
                estado = Estados.AGUARDANDO;
                System.out.println("RECUPERANDO ESTADO: Solicitando estado atual aos outros jogadores...");
                try {
                    canal.send(new Message(null, "SOLICITAR_ESTADO"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        this.ultimaView = novaView;
    }

    private void comecarAuditoria(Address jogador, String nome, Cartela cartela) throws Exception {
        votos.clear();
        jogadorQueGritouBingo = jogador;
        String msg = "AUDITAR:" + nome + "|" + cartela.toString() + "|" + bolasJaSorteadas.toString();
        canal.send(new Message(null, msg));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void receive(Message msg) {
        Object payload = msg.getObject();

        try {
            if (payload instanceof String && ((String)payload).startsWith("ANUNCIO_NOME:")) {
                String nomeRecebido = ((String)payload).substring("ANUNCIO_NOME:".length());
                mapaDeNomes.put(msg.getSrc(), nomeRecebido);
                System.out.println("[INFO] " + getNome(msg.getSrc()) + " ("+msg.getSrc()+") esta no grupo.");
                return;
            }
            
            if (payload instanceof String && payload.equals("SOLICITAR_ESTADO")) {
                if (!souCoordenador) {
                    System.out.println("Novo coordenador pediu o estado. Enviando minhas informacoes...");
                    HashMap<String, Serializable> meuEstado = new HashMap<>();
                    meuEstado.put("bolas", (Serializable) bolasJaSorteadas);
                    meuEstado.put("cartela", minhaCartela);
                    canal.send(new Message(msg.getSrc(), meuEstado));
                }
            }
            else if (payload instanceof HashMap) {
                HashMap<String, Serializable> estadoRecebido = (HashMap<String, Serializable>) payload;
                if(souCoordenador && estado == Estados.AGUARDANDO && estadoRecebido.containsKey("bolas")) {
                    respostasDeEstadoRecebidas++;
                    System.out.println("Recebi informacoes de estado de " + getNome(msg.getSrc()) + ". (" + respostasDeEstadoRecebidas + " de " + (ultimaView.size() -1) + ")");

                    bolasJaSorteadas.addAll((Set<Integer>) estadoRecebido.get("bolas"));
                    
                    Cartela cartelaRecebida = (Cartela) estadoRecebido.get("cartela");
                    if(cartelaRecebida != null) {
                        cartelasJogadores.put(msg.getSrc().toString(), cartelaRecebida);
                    }
                    
                    if(respostasDeEstadoRecebidas >= ultimaView.size() - 1) {
                        estado = Estados.EM_ANDAMENTO;
                        System.out.println("\n================ RECUPERACAO COMPLETA ================");
                        System.out.println("Estado do jogo restaurado. O jogo continua!");
                    }
                }
            }
            else if (payload instanceof String) {
                String conteudo = (String) payload;
                if (conteudo.startsWith("CARTELA_DONO:")) {
                    String[] partes = conteudo.split(":", 3);
                    if (meuEndereco.toString().equals(partes[1])) {
                        minhaCartela = Cartela.deTexto(partes[2]);
                        System.out.println("\n>>> Voce recebeu sua cartela: " + minhaCartela.getNumeros() + " <<<");
                    }
                } else if (conteudo.equals("JOGO_INICIADO")) {
                    estado = Estados.EM_ANDAMENTO;
                    System.out.println("\nO Jogo Comecou! Boa sorte!");
                } else if (conteudo.startsWith("NUMERO:")) {
                    int num = Integer.parseInt(conteudo.replace("NUMERO:", ""));
                    bolasJaSorteadas.add(num);
                    System.out.println(">> Bola Sorteada: " + num);
                } else if (conteudo.startsWith("BINGO:")) {
                    if (souCoordenador && estado != Estados.AUDITORIA) {
                        String[] partes = conteudo.split("\\|", 2);
                        String nome = partes[0].split(":", 2)[1];
                        System.out.println("\n" + nome + " gritou BINGO! Iniciando auditoria automatica...");
                        comecarAuditoria(msg.getSrc(), nome, Cartela.deTexto(partes[1]));
                    }
                } else if (conteudo.startsWith("AUDITAR:")) {
                    estado = Estados.AUDITORIA;
                    String[] partes = conteudo.split("\\|", 3);
                    String nome = partes[0].split(":")[1];
                    Cartela cartelaAuditada = Cartela.deTexto(partes[1]);
                    String bolasRecebidasStr = partes[2].replaceAll("[\\[\\]\\s]", "");
                    Set<Integer> bolasAuditadas = new HashSet<>();
                    if (!bolasRecebidasStr.isEmpty()) { for(String s : bolasRecebidasStr.split(",")) { bolasAuditadas.add(Integer.parseInt(s.trim())); } }

                    if (meuNome.equals(nome)) return;
                    
                    System.out.println("\n================ INICIO DA AUDITORIA ================");
                    System.out.println("Jogador que gritou BINGO: " + nome);
                    System.out.println("Cartela para verificacao: " + cartelaAuditada.getNumeros());
                    System.out.println("Numeros ja sorteados no jogo: " + bolasAuditadas);
                    System.out.println("-----------------------------------------------------");

                    boolean ehValido = cartelaAuditada.checarCompleta(bolasAuditadas);
                    System.out.println("Verificacao automatica: " + (ehValido ? "VALIDO" : "INVALIDO") + ". Enviando voto...");
                    
                    String votoMsg = (ehValido ? "SIM:" : "NAO:") + meuNome;
                    canal.send(new Message(null, votoMsg));

                } else if (conteudo.startsWith("SIM:") || conteudo.startsWith("NAO:")) {
                    if (!souCoordenador || estado != Estados.AUDITORIA) return;

                    votos.put(msg.getSrc(), conteudo.startsWith("SIM") ? "SIM" : "NAO");
                    System.out.println("Voto computado de " + getNome(msg.getSrc()) + ". Total de votos: " + votos.size());

                    int votantesEsperados = ultimaView.getMembers().size() - 1;

                    // <-- CORREÇÃO DA CONDIÇÃO DE CORRIDA
                    if (votos.size() >= votantesEsperados) {
                        // Copia os dados necessários e limpa o estado de votação IMEDIATAMENTE
                        // para evitar que esta lógica seja executada novamente por um voto atrasado.
                        Address jogadorAuditado = jogadorQueGritouBingo;
                        long sim = votos.values().stream().filter(v -> v.equals("SIM")).count();
                        long totalVotos = votos.size();

                        // Limpa o estado para a próxima
                        votos.clear();
                        jogadorQueGritouBingo = null;

                        System.out.println("\n================ RESULTADO DA AUDITORIA ================");
                        System.out.println("Total de Votos 'SIM': " + sim + " de " + totalVotos);
                        
                        if (sim > votantesEsperados / 2.0) {
                            System.out.println("BINGO CONFIRMADO! O vencedor e " + getNome(jogadorAuditado) + "!");
                            canal.send(new Message(null, "RESET"));
                        } else {
                            System.out.println("BINGO FALSO! O jogador " + getNome(jogadorAuditado) + " foi penalizado.");
                            eliminados.add(jogadorAuditado.toString());
                            canal.send(new Message(jogadorAuditado, "ELIMINADO"));
                            canal.send(new Message(null, "CONTINUAR_JOGO"));
                        }
                    }
                } else if (conteudo.equals("ELIMINADO")) {
                    System.out.println("\n>>> VOCE FOI ELIMINADO DESTA RODADA POR UM BINGO FALSO! <<<");
                    fuiEliminado = true;
                    minhaCartela = null;
                } else if (conteudo.equals("CONTINUAR_JOGO")) {
                     System.out.println("A auditoria falhou. O jogo continua...");
                     estado = Estados.EM_ANDAMENTO;
                     votos.clear();
                     jogadorQueGritouBingo = null;
                }
                else if (conteudo.equals("RESET")) {
                    resetarParaNovaRodada();
                } else if (conteudo.equals("FIM_GERAL")) {
                    estado = Estados.ENCERRADO;
                    System.out.println("O coordenador encerrou o jogo. Ate a proxima!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}