import java.util.*;

public class Cartela {
    private Set<Integer> numeros;       // Números da cartela
    private Set<Integer> marcados = new HashSet<>(); // Números já sorteados

    public Cartela(Set<Integer> numeros) {
        this.numeros = numeros;
    }

    public static Cartela gerar() {
        Random r = new Random();
        Set<Integer> nums = new HashSet<>();
        while (nums.size() < 5) {
            nums.add(r.nextInt(10) + 1); // Números de 1 a 10
        }
        return new Cartela(nums);
    }

    public void marcar(int numero) {
        if (numeros.contains(numero)) {
            marcados.add(numero);
        }
    }

    public boolean estaCompleta() {
        return marcados.containsAll(numeros);
    }

    public Set<Integer> getNumeros() {
        return numeros;
    }

    @Override
    public String toString() {
        return numeros.toString();
    }

    public static Cartela deTexto(String texto) {
        texto = texto.replaceAll("[\\[\\]\\s]", "");
        String[] partes = texto.split(",");
        Set<Integer> numeros = new HashSet<>();
        for (String parte : partes) {
            numeros.add(Integer.parseInt(parte));
        }
        return new Cartela(numeros);
    }
}
