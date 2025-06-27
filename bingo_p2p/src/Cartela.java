import java.io.Serializable;
import java.util.*;

public class Cartela implements Serializable { // <-- ADICIONAR "implements Serializable"

    private static final long serialVersionUID = 1L; // <-- ADICIONAR ESTA LINHA

    private Set<Integer> numeros;
    private transient Set<Integer> marcados = new HashSet<>(); // transient para não ser enviado

    // O resto da classe continua exatamente igual...
    public Cartela(Set<Integer> numeros) {
        this.numeros = numeros;
    }

    public static Cartela gerar() {
        Random r = new Random();
        Set<Integer> nums = new HashSet<>();
        while (nums.size() < 5) {
            nums.add(r.nextInt(10) + 1);
        }
        return new Cartela(nums);
    }

    public void marcar(int numero) {
        if (marcados == null) marcados = new HashSet<>();
        if (numeros.contains(numero)) {
            marcados.add(numero);
        }
    }

    public boolean checarCompleta(Set<Integer> bolasSorteadas) {
        return bolasSorteadas.containsAll(this.numeros);
    }

    public Set<Integer> getNumeros() {
        return numeros;
    }

    @Override
    public String toString() {
        List<Integer> sortedList = new ArrayList<>(numeros);
        Collections.sort(sortedList);
        return sortedList.toString().replaceAll("[\\[\\]\\s]", "");
    }

    public static Cartela deTexto(String texto) {
        texto = texto.replaceAll("[\\[\\]\\s]", "");
        String[] partes = texto.split(",");
        Set<Integer> numeros = new HashSet<>();
        for (String parte : partes) {
            if (!parte.isEmpty()) {
                numeros.add(Integer.parseInt(parte.trim()));
            }
        }
        return new Cartela(numeros);
    }
}