import java.util.*;

public class Cartela {
    private Set<Integer> numeros;
    private Set<Integer> marcados = new HashSet<>();

    public Cartela(Set<Integer> numeros) {
        this.numeros = numeros;
    }

    public static Cartela gerar() {
        Random r = new Random();
        Set<Integer> nums = new HashSet<>();
        while (nums.size() < 5) {
            nums.add(r.nextInt(10) + 1); // números de 1 a 10
        }
        return new Cartela(nums);
    }

    public void registrarBola(int bola) {
        if (numeros.contains(bola)) {
            marcados.add(bola);
        }
    }

    public boolean venceu() {
        return marcados.containsAll(numeros);
    }

    public Set<Integer> getNumeros() {
        return numeros;
    }

    @Override
    public String toString() {
        return numeros.toString();
    }

    public static Cartela fromString(String data) {
        data = data.replaceAll("[\\[\\] ]", "");
        String[] partes = data.split(",");
        Set<Integer> nums = new HashSet<>();
        for (String p : partes) {
            nums.add(Integer.parseInt(p));
        }
        return new Cartela(nums);
    }
}
