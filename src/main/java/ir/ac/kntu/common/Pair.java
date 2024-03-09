package ir.ac.kntu.common;

import java.io.Serializable;
import java.util.Objects;

public class Pair<A, B> implements Serializable {
    private static final long serialVersionUID = 8176288675989092842L;

    /**
     * The first pair element
     */
    protected final A key;

    /**
     * The second pair element
     */
    protected final B value;

    /**
     * Create a new pair
     *
     * @param a the first element
     * @param b the second element
     */
    public Pair(A a, B b) {
        this.key = a;
        this.value = b;
    }

    /**
     * Creates new pair of elements pulling of the necessity to provide corresponding types of the
     * elements supplied.
     *
     * @param a   first element
     * @param b   second element
     * @param <A> the first element type
     * @param <B> the second element type
     * @return new pair
     */
    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<>(a, b);
    }

    /**
     * Get the first element of the pair
     *
     * @return the first element of the pair
     */
    public A getKey() {
        return key;
    }

    /**
     * Get the second element of the pair
     *
     * @return the second element of the pair
     */
    public B getValue() {
        return value;
    }

    /**
     * Assess if this pair contains an element.
     *
     * @param e   The element in question
     * @param <E> the element type
     * @return true if contains the element, false otherwise
     */
    public <E> boolean hasElement(E e) {
        if (e == null) {
            return key == null || value == null;
        }
        else {
            return e.equals(key) || e.equals(value);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (!(o instanceof Pair))
            return false;

        @SuppressWarnings("unchecked")
        Pair<A, B> other = (Pair<A, B>) o;
        return Objects.equals(key, other.key) && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        return "(" + key + "," + value + ")";
    }
}
